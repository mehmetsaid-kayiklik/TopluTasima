import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, serverTimestamp, setDoc } from "firebase/firestore";
import { deleteObject, getBytes, ref, uploadBytes } from "firebase/storage";

const here = dirname(fileURLToPath(import.meta.url));
const firestoreRules = await readFile(resolve(here, "..", "firestore.rules"), "utf8");
const storageRules = await readFile(resolve(here, "..", "storage.rules"), "utf8");
const environment = await initializeTestEnvironment({
  projectId: "toplutasima-sprint6b-test",
  firestore: { rules: firestoreRules, host: "127.0.0.1", port: 8080 },
  storage: { rules: storageRules, host: "127.0.0.1", port: 9199 }
});

const uid = "owner-a";
const vehicleId = "vehicle-1";
const photoId = "photo-1";
const hash = "a".repeat(64);
const storagePath = `users/${uid}/vehicles/${vehicleId}/photos/${photoId}.jpg`;
const photoPath = `users/${uid}/vehicles/${vehicleId}/photos/${photoId}`;
const photo = (overrides = {}) => ({
  ownerUid: uid,
  photoId,
  vehicleId,
  storagePath,
  contentHash: hash,
  mimeType: "image/jpeg",
  width: 1200,
  height: 800,
  sizeBytes: 3,
  sortOrder: 0,
  isPrimary: true,
  schemaVersion: 1,
  revision: 1,
  operationId: "operation-1",
  source: "TOPLU_TASIMA",
  clientUpdatedAt: 1,
  _serverUpdatedAt: serverTimestamp(),
  deletedAt: null,
  ...overrides
});
const metadata = (overrides = {}) => ({
  contentType: "image/jpeg",
  customMetadata: {
    vehicleId,
    photoId,
    contentHash: hash,
    schemaVersion: "1"
  },
  ...overrides
});

try {
  const owner = environment.authenticatedContext(uid);
  const other = environment.authenticatedContext("owner-b");
  const anonymous = environment.unauthenticatedContext();

  await assertSucceeds(setDoc(doc(owner.firestore(), photoPath), photo()));
  await assertFails(setDoc(doc(other.firestore(), photoPath), photo()));
  await assertFails(setDoc(doc(anonymous.firestore(), photoPath), photo()));
  await assertFails(setDoc(doc(owner.firestore(), `${photoPath}-wrong`), photo()));
  await assertFails(setDoc(doc(owner.firestore(), photoPath), photo({ schemaVersion: 2 })));
  await assertFails(setDoc(doc(owner.firestore(), photoPath), photo({ sizeBytes: 5242881 })));
  await assertFails(deleteDoc(doc(owner.firestore(), photoPath)));
  await assertSucceeds(setDoc(doc(owner.firestore(), photoPath), photo({
    revision: 2,
    operationId: "delete-operation",
    isPrimary: false,
    deletedAt: 2
  })));

  const ownerRef = ref(owner.storage(), storagePath);
  await assertSucceeds(uploadBytes(ownerRef, new Uint8Array([1, 2, 3]), metadata()));
  await assertSucceeds(getBytes(ownerRef));
  await assertFails(getBytes(ref(other.storage(), storagePath)));
  await assertFails(getBytes(ref(anonymous.storage(), storagePath)));
  await assertFails(uploadBytes(
    ref(other.storage(), `users/owner-b/vehicles/${vehicleId}/photos/${photoId}.jpg`),
    new Uint8Array([1, 2, 3]),
    metadata()
  ));
  await assertFails(uploadBytes(ownerRef, new Uint8Array([1, 2, 3]), metadata({ contentType: "image/png" })));
  await assertFails(uploadBytes(ownerRef, new Uint8Array(5242881), metadata()));
  await assertFails(uploadBytes(
    ref(owner.storage(), `users/${uid}/vehicles/${vehicleId}/invalid/${photoId}.jpg`),
    new Uint8Array([1, 2, 3]),
    metadata()
  ));
  await assertSucceeds(deleteObject(ownerRef));
} finally {
  await environment.cleanup();
}
