
// 1. Imports

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
    collection,
    doc,
    getDoc,
    getDocs,
    setDoc,
    Timestamp,
    serverTimestamp,
    updateDoc,
    writeBatch,
} from "firebase/firestore";

import {
    ref,
    uploadBytes,
    getBytes,
} from "firebase/storage";



// 2. Test environment setup

let testEnv;



// 3. Constants

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

const snapshotDigest = "a".repeat(64);



// 4. seedCapsule() and seedPhotoCapsule()

async function seedCapsule({
    id = capsuleId,
    sender = senderUid,
    recipient = recipientUid,
    unlocked = false,
    unlockAtMillis = Date.now() + 60 * 60 * 1000,
} = {}) {
    await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();

        await setDoc(doc(db, "capsules", id), {
            capsuleId: id,
            senderId: sender,
            recipientId: recipient,
            lockMode: "TIME",
            unlockAt: Timestamp.fromMillis(unlockAtMillis),
            location: null,
            hasPhoto: false,
            photoPath: null,
            createdAt: Timestamp.now(),
            creationZone: "Asia/Seoul",
            snapshotDigest,
            unlocked,
        });

        await setDoc(doc(db, "protectedContent", id), {
            capsuleId: id,
            text: "Test capsule content",
            photoObjectPath: null,
            snapshotDigest,
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
            senderId: sender,
            recipientId: recipient,
            lockMode: "TIME",
            unlockAt: Timestamp.fromMillis(Date.now() + 60 * 60 * 1000),
            location: null,
            hasPhoto: true,
            photoPath: `capsuleMedia/${sender}/${id}/original`,
            createdAt: Timestamp.now(),
            creationZone: "Asia/Seoul",
            snapshotDigest,
            unlocked,
        });
    });
}



// 5. Firestore tests

describe("Capsule Firestore security rules", () => {

    async function attemptPairedCreate({
        includeMetadata = true,
        includeContent = true,
        metadataDigest = snapshotDigest,
        contentDigest = snapshotDigest,
    } = {}) {
        const db = testEnv.authenticatedContext(senderUid).firestore();

        const metadata = {
            capsuleId,
            senderId: senderUid,
            recipientId: recipientUid,
            lockMode: "TIME",
            unlockAt: Timestamp.fromMillis(Date.now() + 60 * 60 * 1000),
            location: null,
            hasPhoto: false,
            photoPath: null,
            createdAt: serverTimestamp(),
            creationZone: "Asia/Seoul",
            snapshotDigest: metadataDigest,
            unlocked: false,
        };

        const content = {
            capsuleId,
            text: "Test capsule content",
            photoObjectPath: null,
            snapshotDigest: contentDigest,
        };

        const batch = writeBatch(db);

        if (includeMetadata) {
            batch.set(doc(db, "capsules", capsuleId), metadata);
        }

        if (includeContent) {
            batch.set(doc(db, "protectedContent", capsuleId), content);
        }

        return batch.commit();
      }



    // Test: Sender creates a capsule; recipient cannot read locked content.
    it("allows the sender to create a capsule but keeps its content locked from the recipient", async () => {

        const senderDb = testEnv
        .authenticatedContext(senderUid)
        .firestore();

        const recipientDb = testEnv
            .authenticatedContext(recipientUid)
            .firestore();

        const metadata = {
            capsuleId,
            senderId: senderUid,
            recipientId: recipientUid,
            lockMode: "TIME",
            unlockAt: Timestamp.fromMillis(Date.now() + 60 * 60 * 1000),
            location: null,
            hasPhoto: false,
            photoPath: null,
            createdAt: serverTimestamp(),
            creationZone: "Asia/Seoul",
            snapshotDigest,
            unlocked: false,
        };

        const content = {
            capsuleId,
            text: "Hello from the past!",
            photoObjectPath: null,
            snapshotDigest,
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

    // Test: Metadata cannot be created without protected content.
    it("denies capsule metadata creation without protected content", async () => {
        await assertFails(
            attemptPairedCreate({ includeContent: false })
        );
    });

    // Test: Protected content cannot be created without metadata.
    it("denies protected content creation without capsule metadata", async () => {
        await assertFails(
            attemptPairedCreate({ includeMetadata: false })
        );
    });

    // Test: Metadata and content must have matching snapshot digests.
    it("denies paired creation with mismatched snapshot digests", async () => {
        await assertFails(
            attemptPairedCreate({
                metadataDigest: "a".repeat(64),
                contentDigest: "b".repeat(64),
            })
        );
    });

    // Test: Unrelated users cannot read capsule metadata.
    it("denies metadata access to unrelated users", async () => {
        await seedCapsule();

        const outsiderDb = testEnv
            .authenticatedContext("outsider-c")
            .firestore();

        await assertFails(
            getDoc(doc(outsiderDb, "capsules", capsuleId))
        );
    });

    // Test: Unrelated users cannot list capsules.
    it("denies capsule listing to unrelated users", async () => {
        await seedCapsule();

        const outsiderDb = testEnv
            .authenticatedContext("outsider-c")
            .firestore();

        await assertFails(
            getDocs(collection(outsiderDb, "capsules"))
        );
    });

    // Test: Recipient can read content after unlocking.
    it("allows the recipient to read content after unlocking", async () => {
        await seedCapsule({ unlocked: true });

        const recipientDb = testEnv
            .authenticatedContext(recipientUid)
            .firestore();

        await assertSucceeds(
            getDoc(doc(recipientDb, "protectedContent", capsuleId))
        );
    });

    // Test: Users cannot read their own locked self-sent capsules.
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

    // Test: Recipient can unlock a capsule after its unlock time.
    it("allows the recipient to unlock a TIME capsule after its unlock time", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertSucceeds(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
            })
        );
    });

    // Test: Recipient cannot unlock an early capsule.
    it("denies the recipient an early unlock", async () => {
        await seedCapsule();

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
            })
        );
    });

    // Test: Sender cannot unlock a capsule.
    it("denies the sender from unlocking a capsule", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(senderUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
            })
        );
    });

    // Test: Unrelated users cannot unlock a capsule.
    it("denies an unrelated user from unlocking a capsule", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext("outsider-c").firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
            })
        );
    });

    // Test: Recipient cannot relock an unlocked capsule.
    it("denies relocking an unlocked capsule", async () => {
        await seedCapsule({ unlocked: true });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: false,
            })
        );
    });

    // Test: Sender cannot change capsule metadata while unlocking.
    it("denies changing capsule metadata while unlocking", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                title: "Changed title",
            })
        );
    });

    // Test: Users cannot list the recipient directory.
    it("denies listing the recipient directory", async () => {
        const db = testEnv
            .authenticatedContext(senderUid)
            .firestore();

        await assertFails(
            getDocs(collection(db, "recipientDirectory"))
        );
    });
});



// 6. Storage tests

describe("Capsule Storage security rules", () => {
    function photoPathFor(id) {
        return `capsuleMedia/${senderUid}/${id}/original`;
    }

    const imageData = new Uint8Array([255, 216, 255, 224]);

    // Test: Sender creates a capsule; recipient cannot read locked content.
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

    // Test: Unrelated users cannot read capsule metadata.
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

    // Test: Recipient can read content after unlocking.
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

    // Test: Users cannot read their own locked self-sent capsules.
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

    // Test: Recipient can unlock a capsule after its unlock time.
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

    // Test: Unrelated users cannot download photos.
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

    // Test: Recipient can unlock a capsule after its unlock time.
    it("denies uploads with unsupported file types", async () => {
        const id = "storage-invalid-type-test";
        await seedPhotoCapsule({ id });

        const senderStorage = testEnv
            .authenticatedContext(senderUid)
            .storage();

        await assertFails(
            uploadBytes(
                ref(senderStorage, photoPathFor(id)),
                imageData,
                { contentType: "application/pdf" }
            )
        );
    });

    //Test: Recipient can unlock a capsule after its unlock time.
    it("denies photos larger than 5 MB", async () => {
        const id = "storage-oversized-test";
        await seedPhotoCapsule({ id });

        const senderStorage = testEnv
            .authenticatedContext(senderUid)
            .storage();

        const oversizedImage = new Uint8Array(
           5 * 1024 * 1024 + 1
        );

        await assertFails(
            uploadBytes(
                ref(senderStorage, photoPathFor(id)),
                oversizedImage,
                { contentType: "image/jpeg" }
            )
        );
    });

    // Test: Sender creates a capsule; recipient cannot read locked content.
    it('allows sender to upload a photo before capsule publication', async () => {
        const senderStorage = testEnv
            .authenticatedContext('senderA')
            .storage();

        const photoRef = ref(
            senderStorage,
            'capsuleMedia/senderA/capsule-before-publication/original'
        );

        const photoBytes = new Uint8Array([0xFF, 0xD8, 0xFF]);

        await assertSucceeds(
            uploadBytes(photoRef, photoBytes, {
                contentType: 'image/jpeg',
            })
        );
    });

    // Test: Unrelated users cannot read capsule metadata.
    it('denies uploading a photo under another users UID', async () => {
        const userBStorage = testEnv
            .authenticatedContext('senderB')
            .storage();

        const photoRef = ref(
            userBStorage,
            'capsuleMedia/senderA/capsule-unauthorized/original'
        );

        await assertFails(
            uploadBytes(photoRef, new Uint8Array([0xFF, 0xD8, 0xFF]), {
                contentType: 'image/jpeg',
            })
        );
    });

    // Test: Recipient can unlock a capsule after its unlock time.
    it('denies photo uploads when signed out', async () => {
        const signedOutStorage = testEnv.unauthenticatedContext().storage();

        const photoRef = ref(
            signedOutStorage,
            'capsuleMedia/senderA/capsule-signed-out/original'
        );

        await assertFails(
            uploadBytes(photoRef, new Uint8Array([0xFF, 0xD8, 0xFF]), {
                contentType: 'image/jpeg',
            })
        );
    });
});
