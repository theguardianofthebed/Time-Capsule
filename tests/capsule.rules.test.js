
import { readFileSync } from "node:fs";
import {
  afterAll,
  beforeAll,
  beforeEach,
  describe,
  it,
} from "vitest";

import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";

import {
  doc,
  getDoc,
  setDoc,
  Timestamp,
  updateDoc,
  writeBatch,
} from "firebase/firestore";

import {
  ref,
  uploadBytes,
  getBytes,
} from "firebase/storage";

let testEnv;

const capsuleId = "test-capsule-001";
const senderUid = "sender-a";
const recipientUid = "recipient-b";

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-timecapsule",

    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: readFileSync("firestore.rules", "utf8"),
    },

    storage: {
      host: "127.0.0.1",
      port: 9199,
      rules: readFileSync("storage.rules", "utf8"),
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.clearStorage();

  // Create the recipient directory entry for our test.
  // Security rules are disabled only while preparing test data.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "recipientDirectory", recipientUid),
      {
        uid: recipientUid,
        displayName: "Recipient B",
      }
    );
  });
});

async function seedCapsule({
  id = capsuleId,
  sender = senderUid,
  recipient = recipientUid,
  unlocked = false,
} = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "capsules", id), {
      capsuleId: id,
      senderUid: sender,
      recipientUid: recipient,
      lockMode: "TIME",
      unlockAt: Timestamp.fromMillis(Date.now() + 60 * 60 * 1000),
      location: null,
      hasPhoto: false,
      photoPath: null,
      createdAt: Timestamp.now(),
      unlocked,
    });

    await setDoc(doc(db, "protectedContent", id), {
      capsuleId: id,
      text: "Test capsule content",
      photoObjectPath: null,
    });
  });
}

async function seedPhotoCapsule({
  id = capsuleId,
  sender = senderUid,
  recipient = recipientUid,
  unlocked = false,
} = {}) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "capsules", id), {
      capsuleId: id,
      senderUid: sender,
      recipientUid: recipient,
      lockMode: "TIME",
      unlockAt: Timestamp.fromMillis(Date.now() + 60 * 60 * 1000),
      location: null,
      hasPhoto: true,
      photoPath: `capsuleMedia/${sender}/${id}/original`,
      createdAt: Timestamp.now(),
      unlocked,
    });
  });
}

describe("Capsule Firestore security rules", () => {
  // Test 1: Sender creates a capsule; recipient cannot read locked content.
  it("allows the sender to create a capsule but keeps its content locked from the recipient", async () => {
    const senderDb = testEnv
      .authenticatedContext(senderUid)
      .firestore();

    const recipientDb = testEnv
      .authenticatedContext(recipientUid)
      .firestore();

    const metadata = {
      capsuleId,
      senderUid,
      recipientUid,
      lockMode: "TIME",
      unlockAt: Timestamp.fromMillis(Date.now() + 60 * 60 * 1000),
      location: null,
      hasPhoto: false,
      photoPath: null,
      createdAt: Timestamp.now(),
      unlocked: false,
    };

    const content = {
      capsuleId,
      text: "Hello from the past!",
      photoObjectPath: null,
    };

    const batch = writeBatch(senderDb);

    batch.set(doc(senderDb, "capsules", capsuleId), metadata);
    batch.set(doc(senderDb, "protectedContent", capsuleId), content);

    await assertSucceeds(batch.commit());

    await assertSucceeds(
      getDoc(doc(senderDb, "protectedContent", capsuleId))
    );

    await assertFails(
      getDoc(doc(recipientDb, "protectedContent", capsuleId))
    );
  });

  // Test 2: Unrelated users cannot read capsule metadata.
  it("denies metadata access to unrelated users", async () => {
    await seedCapsule();

    const outsiderDb = testEnv
      .authenticatedContext("outsider-c")
      .firestore();

    await assertFails(
      getDoc(doc(outsiderDb, "capsules", capsuleId))
    );
  });

  // Test 3: Recipient can read content after unlocking.
  it("allows the recipient to read content after unlocking", async () => {
    await seedCapsule({ unlocked: true });

    const recipientDb = testEnv
      .authenticatedContext(recipientUid)
      .firestore();

    await assertSucceeds(
      getDoc(doc(recipientDb, "protectedContent", capsuleId))
    );
  });

  // Test 4: Users cannot read their own locked self-sent capsules.
  it("denies access to a locked self-sent capsule", async () => {
    const selfUid = "self-user";

    await seedCapsule({
      id: "self-capsule",
      sender: selfUid,
      recipient: selfUid,
    });

    const selfDb = testEnv
      .authenticatedContext(selfUid)
      .firestore();

    await assertFails(
      getDoc(doc(selfDb, "protectedContent", "self-capsule"))
    );
  });

  // Test 5: Recipients cannot unlock capsules directly.
  it("denies client-side unlock attempts", async () => {
    await seedCapsule();

    const recipientDb = testEnv
      .authenticatedContext(recipientUid)
      .firestore();

    await assertFails(
      updateDoc(doc(recipientDb, "capsules", capsuleId), {
        unlocked: true,
      })
    );
  });
});

describe("Capsule Storage security rules", () => {
  function photoPathFor(id) {
    return `capsuleMedia/${senderUid}/${id}/original`;
  }

  const imageData = new Uint8Array([255, 216, 255, 224]);

  it("allows the sender to upload a photo", async () => {
    const id = "storage-upload-test";
    await seedPhotoCapsule({ id });

    const senderStorage = testEnv
      .authenticatedContext(senderUid)
      .storage();

    await assertSucceeds(
      uploadBytes(
        ref(senderStorage, photoPathFor(id)),
        imageData,
        { contentType: "image/jpeg" }
      )
    );
  });

  it("denies photo uploads from unrelated users", async () => {
    const id = "storage-outsider-test";
    await seedPhotoCapsule({ id });

    const outsiderStorage = testEnv
      .authenticatedContext("outsider-c")
      .storage();

    await assertFails(
      uploadBytes(
        ref(outsiderStorage, photoPathFor(id)),
        imageData,
        { contentType: "image/jpeg" }
      )
    );
  });

  it("denies overwriting an existing photo", async () => {
    const id = "storage-overwrite-test";
    await seedPhotoCapsule({ id });

    const senderStorage = testEnv
      .authenticatedContext(senderUid)
      .storage();

    const photoRef = ref(senderStorage, photoPathFor(id));

    await assertSucceeds(
      uploadBytes(photoRef, imageData, {
        contentType: "image/jpeg",
      })
    );

    await assertFails(
      uploadBytes(photoRef, imageData, {
        contentType: "image/jpeg",
      })
    );
  });

  it("denies the recipient from downloading a locked photo", async () => {
    const id = "storage-locked-test";
    await seedPhotoCapsule({ id });

    const senderStorage = testEnv
      .authenticatedContext(senderUid)
      .storage();

    const recipientStorage = testEnv
      .authenticatedContext(recipientUid)
      .storage();

    await assertSucceeds(
      uploadBytes(
        ref(senderStorage, photoPathFor(id)),
        imageData,
        { contentType: "image/jpeg" }
      )
    );

    await assertFails(
      getBytes(ref(recipientStorage, photoPathFor(id)))
    );
  });

  it("allows the recipient to download an unlocked photo", async () => {
    const id = "storage-unlocked-test";
    await seedPhotoCapsule({ id, unlocked: true });

    const senderStorage = testEnv
      .authenticatedContext(senderUid)
      .storage();

    const recipientStorage = testEnv
      .authenticatedContext(recipientUid)
      .storage();

    await assertSucceeds(
      uploadBytes(
        ref(senderStorage, photoPathFor(id)),
        imageData,
        { contentType: "image/jpeg" }
      )
    );

    await assertSucceeds(
      getBytes(ref(recipientStorage, photoPathFor(id)))
    );
  });

  it("denies unrelated users from downloading photos", async () => {
    const id = "storage-download-outsider-test";
    await seedPhotoCapsule({ id, unlocked: true });

    const senderStorage = testEnv
      .authenticatedContext(senderUid)
      .storage();

    const outsiderStorage = testEnv
      .authenticatedContext("outsider-c")
      .storage();

    await assertSucceeds(
      uploadBytes(
        ref(senderStorage, photoPathFor(id)),
        imageData,
        { contentType: "image/jpeg" }
      )
    );

    await assertFails(
      getBytes(ref(outsiderStorage, photoPathFor(id)))
    );
  });
});
