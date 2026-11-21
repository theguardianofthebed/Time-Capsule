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
    query,
    setDoc,
    Timestamp,
    serverTimestamp,
    updateDoc,
    where,
    writeBatch,
} from "firebase/firestore";

import {
    ref,
    uploadBytes,
    getBytes,
    deleteObject,
} from "firebase/storage";



// 2. Test environment setup

let testEnv;



// 3. Constants

const capsuleId = "test-capsule-001";
const senderUid = "sender-a";
const recipientUid = "recipient-b";
const snapshotDigest = "a".repeat(64);

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

    // Create the recipient directory entries for our test data.
    await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();
        await setDoc(
            doc(db, "recipientDirectory", recipientUid),
            {
                uid: recipientUid,
                displayName: "Recipient B",
            }
        );
        await setDoc(
            doc(db, "recipientDirectory", senderUid),
            {
                uid: senderUid,
                displayName: "Sender A",
            }
        );
    });
});



// 4. Test seed functions

async function seedCapsule({
    id = capsuleId,
    sender = senderUid,
    recipient = recipientUid,
    unlocked = false,
    unlockedAt = null,
    unlockAtMillis = Date.now() + 60 * 60 * 1000,
    lockMode = "TIME",
} = {}) {
    await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();

        const metadata = {
            capsuleId: id,
            senderId: sender,
            recipientId: recipient,
            lockMode,
            unlockAt: lockMode === "LOCATION" ? null : Timestamp.fromMillis(unlockAtMillis),
            location: null,
            hasPhoto: false,
            photoPath: null,
            createdAt: Timestamp.now(),
            creationZone: "Asia/Seoul",
            snapshotDigest,
            unlocked,
        };
        if (unlockedAt !== null) {
            metadata.unlockedAt = unlockedAt;
        }

        await setDoc(doc(db, "capsules", id), metadata);

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
    unlockedAt = null,
} = {}) {
    await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();

        const metadata = {
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
        };
        if (unlockedAt !== null) {
            metadata.unlockedAt = unlockedAt;
        }

        await setDoc(doc(db, "capsules", id), metadata);
    });
}



// 5. Firestore security rules tests

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

    // Recipient directory tests

    it("allows an authenticated user to look up another user's recipient directory profile by exact UID", async () => {
        const senderDb = testEnv.authenticatedContext(senderUid).firestore();

        await assertSucceeds(
            getDoc(doc(senderDb, "recipientDirectory", recipientUid))
        );
    });

    it("denies listing the recipient directory", async () => {
        const senderDb = testEnv.authenticatedContext(senderUid).firestore();

        await assertFails(
            getDocs(collection(senderDb, "recipientDirectory"))
        );
    });

    it("denies signed-out access to recipient directory profiles", async () => {
        const unauthDb = testEnv.unauthenticatedContext().firestore();

        await assertFails(
            getDoc(doc(unauthDb, "recipientDirectory", recipientUid))
        );
    });

    it("denies user A from modifying recipient B's directory profile", async () => {
        const senderDb = testEnv.authenticatedContext(senderUid).firestore();

        await assertFails(
            setDoc(doc(senderDb, "recipientDirectory", recipientUid), {
                uid: recipientUid,
                displayName: "Forged Name",
            })
        );
    });

    // Capsule creation & access tests

    it("allows the sender to create a capsule but keeps its content locked from the recipient", async () => {
        const senderDb = testEnv.authenticatedContext(senderUid).firestore();
        const recipientDb = testEnv.authenticatedContext(recipientUid).firestore();

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

    it("denies capsule metadata creation without protected content", async () => {
        await assertFails(
            attemptPairedCreate({ includeContent: false })
        );
    });

    it("denies protected content creation without capsule metadata", async () => {
        await assertFails(
            attemptPairedCreate({ includeMetadata: false })
        );
    });

    it("denies paired creation with mismatched snapshot digests", async () => {
        await assertFails(
            attemptPairedCreate({
                metadataDigest: "a".repeat(64),
                contentDigest: "b".repeat(64),
            })
        );
    });

    it("denies metadata access to unrelated users", async () => {
        await seedCapsule();

        const outsiderDb = testEnv.authenticatedContext("outsider-c").firestore();

        await assertFails(
            getDoc(doc(outsiderDb, "capsules", capsuleId))
        );
    });

    it("denies capsule listing to unrelated users", async () => {
        await seedCapsule();

        const outsiderDb = testEnv.authenticatedContext("outsider-c").firestore();

        await assertFails(
            getDocs(collection(outsiderDb, "capsules"))
        );
    });

    it("allows the sender to list their own capsules", async () => {
        await seedCapsule();

        const senderDb = testEnv.authenticatedContext(senderUid).firestore();

        const senderQuery = query(
            collection(senderDb, "capsules"),
            where("senderId", "==", senderUid)
        );

        await assertSucceeds(getDocs(senderQuery));
    });

    it("allows the recipient to list capsules addressed to them", async () => {
        await seedCapsule();

        const recipientDb = testEnv.authenticatedContext(recipientUid).firestore();

        const recipientQuery = query(
            collection(recipientDb, "capsules"),
            where("recipientId", "==", recipientUid)
        );

        await assertSucceeds(getDocs(recipientQuery));
    });

    it("denies unrestricted capsule queries", async () => {
        await seedCapsule();

        const senderDb = testEnv.authenticatedContext(senderUid).firestore();

        await assertFails(
            getDocs(collection(senderDb, "capsules"))
        );
    });

    it("allows the recipient to read content after unlocking", async () => {
        await seedCapsule({ unlocked: true, unlockedAt: Timestamp.now() });

        const recipientDb = testEnv.authenticatedContext(recipientUid).firestore();

        await assertSucceeds(
            getDoc(doc(recipientDb, "protectedContent", capsuleId))
        );
    });

    it("denies access to a locked self-sent capsule", async () => {
        const selfUid = "self-user";

        await seedCapsule({
            id: "self-capsule",
            sender: selfUid,
            recipient: selfUid,
        });

        const selfDb = testEnv.authenticatedContext(selfUid).firestore();

        await assertFails(
            getDoc(doc(selfDb, "protectedContent", "self-capsule"))
        );
    });

    // One-way unlock rules & unlockedAt server timestamp tests

    it("allows the recipient to unlock a TIME capsule after its unlock time with unlockedAt server timestamp", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertSucceeds(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                unlockedAt: serverTimestamp(),
            })
        );
    });

    it("denies recipient unlock without unlockedAt timestamp", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
            })
        );
    });

    it("denies recipient unlock with a client-provided timestamp instead of serverTimestamp()", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                unlockedAt: Timestamp.now(),
            })
        );
    });

    it("denies the recipient an early unlock", async () => {
        await seedCapsule();

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                unlockedAt: serverTimestamp(),
            })
        );
    });

    it("denies the sender from unlocking a capsule", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(senderUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                unlockedAt: serverTimestamp(),
            })
        );
    });

    it("denies an unrelated user from unlocking a capsule", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext("outsider-c").firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                unlockedAt: serverTimestamp(),
            })
        );
    });

    it("denies relocking an unlocked capsule", async () => {
        await seedCapsule({ unlocked: true, unlockedAt: Timestamp.now() });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: false,
            })
        );
    });

    it("denies changing capsule metadata while unlocking", async () => {
        await seedCapsule({
            unlockAtMillis: Date.now() - 60 * 1000,
        });

        const db = testEnv.authenticatedContext(recipientUid).firestore();

        await assertFails(
            updateDoc(doc(db, "capsules", capsuleId), {
                unlocked: true,
                unlockedAt: serverTimestamp(),
                title: "Changed title",
            })
        );
    });
});



// 6. Storage security rules tests

describe("Capsule Storage security rules", () => {
    function photoPathFor(id) {
        return `capsuleMedia/${senderUid}/${id}/original`;
    }

    const imageData = new Uint8Array([255, 216, 255, 224]);

    it("allows the sender to upload a photo before capsule publication", async () => {
        const id = "storage-prepub-upload-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();

        await assertSucceeds(
            uploadBytes(
                ref(senderStorage, photoPathFor(id)),
                imageData,
                { contentType: "image/jpeg" }
            )
        );
    });

    it("allows the sender to retry uploading a photo before capsule publication", async () => {
        const id = "storage-prepub-retry-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        // Prepublication retry (re-upload before Firestore batch) succeeds
        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );
    });

    it("allows the sender to read their own photo before capsule publication", async () => {
        const id = "storage-prepub-read-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        await assertSucceeds(
            getBytes(photoRef)
        );
    });

    it("denies photo uploads from unrelated users", async () => {
        const id = "storage-outsider-test";
        const outsiderStorage = testEnv.authenticatedContext("outsider-c").storage();

        await assertFails(
            uploadBytes(
                ref(outsiderStorage, photoPathFor(id)),
                imageData,
                { contentType: "image/jpeg" }
            )
        );
    });

    it("denies overwriting an existing photo after capsule publication (immutable)", async () => {
        const id = "storage-overwrite-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        // 1. Upload photo before publication
        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        // 2. Publish capsule to Firestore
        await seedPhotoCapsule({ id });

        // 3. Attempt to overwrite published photo -> fails (immutable)
        await assertFails(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );
    });

    it("allows the owner to remove an unpublished orphan", async () => {
        const storage = testEnv.authenticatedContext(senderUid).storage();
        const photoRef = ref(storage, photoPathFor("orphan-cleanup"));
        await assertSucceeds(uploadBytes(photoRef, imageData, { contentType: "image/png" }));
        await assertSucceeds(deleteObject(photoRef));
    });

    it("denies another account deleting an unpublished orphan", async () => {
        const path = photoPathFor("orphan-other-account");
        const owner = testEnv.authenticatedContext(senderUid).storage();
        const other = testEnv.authenticatedContext("unrelated-c").storage();
        await assertSucceeds(uploadBytes(ref(owner, path), imageData, { contentType: "image/png" }));
        await assertFails(deleteObject(ref(other, path)));
    });

    it("denies signed-out deletion of an unpublished orphan", async () => {
        const path = photoPathFor("orphan-signed-out");
        const owner = testEnv.authenticatedContext(senderUid).storage();
        const anonymous = testEnv.unauthenticatedContext().storage();
        await assertSucceeds(uploadBytes(ref(owner, path), imageData, { contentType: "image/png" }));
        await assertFails(deleteObject(ref(anonymous, path)));
    });

    it("denies empty original uploads", async () => {
        const storage = testEnv.authenticatedContext(senderUid).storage();
        await assertFails(uploadBytes(ref(storage, photoPathFor("empty-photo")),
            new Uint8Array(0), { contentType: "image/png" }));
    });

    it("denies deleting a photo after capsule publication (immutable)", async () => {
        const id = "storage-delete-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        await seedPhotoCapsule({ id });

        await assertFails(
            deleteObject(photoRef)
        );
    });

    it("denies the recipient from downloading a locked photo", async () => {
        const id = "storage-locked-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const recipientStorage = testEnv.authenticatedContext(recipientUid).storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        await seedPhotoCapsule({ id, unlocked: false });

        await assertFails(
            getBytes(ref(recipientStorage, photoPathFor(id)))
        );
    });

    it("allows the recipient to download an unlocked photo", async () => {
        const id = "storage-unlocked-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const recipientStorage = testEnv.authenticatedContext(recipientUid).storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        // Upload photo before publication
        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        // Publish unlocked capsule
        await seedPhotoCapsule({ id, unlocked: true, unlockedAt: Timestamp.now() });

        // Recipient can download unlocked photo
        await assertSucceeds(
            getBytes(ref(recipientStorage, photoPathFor(id)))
        );
    });

    it("denies unrelated users from downloading photos", async () => {
        const id = "storage-download-outsider-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const outsiderStorage = testEnv.authenticatedContext("outsider-c").storage();
        const photoRef = ref(senderStorage, photoPathFor(id));

        await assertSucceeds(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );

        await seedPhotoCapsule({ id, unlocked: true, unlockedAt: Timestamp.now() });

        await assertFails(
            getBytes(ref(outsiderStorage, photoPathFor(id)))
        );
    });

    it("denies uploads with unsupported file types", async () => {
        const id = "storage-invalid-type-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();

        await assertFails(
            uploadBytes(
                ref(senderStorage, photoPathFor(id)),
                imageData,
                { contentType: "application/pdf" }
            )
        );
    });

    it("denies photos larger than 5 MB", async () => {
        const id = "storage-oversized-test";
        const senderStorage = testEnv.authenticatedContext(senderUid).storage();
        const oversizedImage = new Uint8Array(5 * 1024 * 1024 + 1);

        await assertFails(
            uploadBytes(
                ref(senderStorage, photoPathFor(id)),
                oversizedImage,
                { contentType: "image/jpeg" }
            )
        );
    });

    it("denies uploading a photo under another user's UID", async () => {
        const userBStorage = testEnv.authenticatedContext("senderB").storage();
        const photoRef = ref(
            userBStorage,
            "capsuleMedia/senderA/capsule-unauthorized/original"
        );

        await assertFails(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );
    });

    it("denies photo uploads when signed out", async () => {
        const signedOutStorage = testEnv.unauthenticatedContext().storage();
        const photoRef = ref(
            signedOutStorage,
            "capsuleMedia/senderA/capsule-signed-out/original"
        );

        await assertFails(
            uploadBytes(photoRef, imageData, { contentType: "image/jpeg" })
        );
    });
});
