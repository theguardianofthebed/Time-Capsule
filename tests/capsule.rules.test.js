
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
