import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, serverTimestamp, setDoc } from "firebase/firestore";

const here = dirname(fileURLToPath(import.meta.url));
const rules = await readFile(resolve(here, "..", "firestore.rules"), "utf8");
const environment = await initializeTestEnvironment({
  projectId: "toplutasima-sprint6b-test",
  firestore: { rules }
});

const uid = "owner-a";
const vehicleId = "vehicle-1";
const assignment = (overrides = {}) => ({
  vehicleId,
  personId: "person-1",
  schemaVersion: 1,
  revision: 1,
  operationId: "operation-1",
  source: "BELLEK",
  clientUpdatedAt: 1,
  _serverUpdatedAt: serverTimestamp(),
  deletedAt: null,
  ...overrides
});

try {
  const ownerDb = environment.authenticatedContext(uid).firestore();
  const otherDb = environment.authenticatedContext("owner-b").firestore();
  const unauthenticatedDb = environment.unauthenticatedContext().firestore();
  const path = `users/${uid}/vehicleAssignments/${vehicleId}`;

  await assertSucceeds(setDoc(doc(ownerDb, path), assignment()));
  await assertFails(setDoc(doc(otherDb, path), assignment()));
  await assertFails(setDoc(doc(unauthenticatedDb, path), assignment()));
  await assertFails(setDoc(doc(ownerDb, `users/${uid}/vehicleAssignments/other`), assignment()));
  await assertFails(setDoc(doc(ownerDb, path), assignment({ schemaVersion: 2 })));
  await assertFails(setDoc(doc(ownerDb, path), assignment({ revision: -1 })));
  await assertFails(setDoc(doc(ownerDb, path), assignment({ source: "UNTRUSTED" })));
  await assertFails(setDoc(doc(ownerDb, path), assignment({ personId: null })));
  await assertFails(setDoc(doc(ownerDb, path), { ...assignment(), unexpected: true }));
  await assertFails(deleteDoc(doc(ownerDb, path)));

  await assertSucceeds(setDoc(doc(ownerDb, path), assignment({
    personId: "person-1",
    deletedAt: 2,
    revision: 2,
    operationId: "operation-2"
  })));
  await assertSucceeds(setDoc(doc(ownerDb, path), assignment({
    personId: "person-2",
    deletedAt: null,
    revision: 3,
    operationId: "operation-3"
  })));
  await assertFails(setDoc(doc(ownerDb, path), assignment({ revision: 2 })));
  assert.ok(true);
} finally {
  await environment.cleanup();
}
