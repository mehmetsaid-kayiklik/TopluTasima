import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc } from "firebase/firestore";

const here = dirname(fileURLToPath(import.meta.url));
const rules = await readFile(resolve(here, "..", "firestore.rules"), "utf8");
const environment = await initializeTestEnvironment({
  projectId: "toplutasima-sprint7a-test",
  firestore: { rules }
});

const uid = "owner-a";
const vehicleId = "vehicle-1";
const operation1 = "11111111-1111-4111-8111-111111111111";
const operation2 = "22222222-2222-4222-8222-222222222222";
const common = (overrides = {}) => ({
  ownerUid: uid,
  vehicleId,
  schemaVersion: 1,
  revision: 1,
  operationId: operation1,
  source: "MANUAL",
  createdAt: 1,
  clientUpdatedAt: 1,
  _serverUpdatedAt: serverTimestamp(),
  deletedAt: null,
  ...overrides
});

const odometer = (overrides = {}) => ({
  ...common(),
  odometerEntryId: "odometer-1",
  observedAt: 1,
  odometerMeters: 120000,
  quality: "CONFIRMED",
  readingRole: "MANUAL",
  odometerSeriesId: "series-1",
  sourceRecordType: null,
  sourceRecordId: null,
  correctionOfEntryId: null,
  resetReason: null,
  notes: null,
  ...overrides
});

const expense = (overrides = {}) => ({
  ...common(),
  expenseId: "expense-1",
  occurredAt: 1,
  category: "PARKING",
  transactionKind: "EXPENSE",
  amountMinor: 1250,
  currencyCode: "EUR",
  currencyExponent: 2,
  vendorName: null,
  notes: null,
  referenceNumber: null,
  periodStartEpochDay: null,
  periodEndEpochDay: null,
  dueEpochDay: null,
  odometerEntryId: null,
  odometerMetersSnapshot: null,
  splitGroupId: null,
  duplicateFingerprint: null,
  relatedExpenseId: null,
  ...overrides
});

const reminder = (overrides = {}) => ({
  ...common(),
  reminderId: "reminder-1",
  title: "Inspection",
  reminderType: "INSPECTION",
  status: "ACTIVE",
  dueEpochDay: 21000,
  dueOdometerMeters: null,
  recurrenceMonths: null,
  recurrenceDistanceMeters: null,
  recurrenceAnchor: "LAST_COMPLETION",
  leadDays: 14,
  leadDistanceMeters: null,
  snoozedUntilEpochDay: null,
  linkedServiceRecordId: null,
  lastCompletedServiceRecordId: null,
  lastCompletedAt: null,
  lastCompletedOdometerMeters: null,
  notes: null,
  ...overrides
});

try {
  const ownerDb = environment.authenticatedContext(uid).firestore();
  const otherDb = environment.authenticatedContext("owner-b").firestore();
  const anonymousDb = environment.unauthenticatedContext().firestore();
  const odometerPath = `users/${uid}/vehicleOdometerEntries/odometer-1`;
  const expensePath = `users/${uid}/vehicleExpenses/expense-1`;
  const reminderPath = `users/${uid}/vehicleReminders/reminder-1`;

  await assertSucceeds(setDoc(doc(ownerDb, odometerPath), odometer()));
  await assertSucceeds(getDoc(doc(ownerDb, odometerPath)));
  await assertFails(getDoc(doc(otherDb, odometerPath)));
  await assertFails(getDoc(doc(anonymousDb, odometerPath)));
  await assertFails(setDoc(doc(otherDb, odometerPath), odometer()));
  await assertFails(setDoc(doc(ownerDb, odometerPath), odometer({ ownerUid: "owner-b" })));
  await assertFails(setDoc(doc(ownerDb, odometerPath), odometer({ odometerEntryId: "other" })));
  await assertFails(setDoc(doc(ownerDb, odometerPath), odometer({ odometerMeters: 1.5 })));
  await assertFails(setDoc(doc(ownerDb, odometerPath), odometer({ revision: -1 })));
  await assertFails(setDoc(doc(ownerDb, odometerPath), odometer({ source: "UNTRUSTED" })));
  await assertFails(deleteDoc(doc(ownerDb, odometerPath)));
  await assertSucceeds(setDoc(doc(ownerDb, odometerPath), odometer({
    revision: 2,
    operationId: operation2,
    deletedAt: 2
  })));
  await assertFails(setDoc(doc(ownerDb, odometerPath), odometer({ revision: 1 })));

  await assertSucceeds(setDoc(doc(ownerDb, expensePath), expense()));
  await assertFails(setDoc(doc(ownerDb, expensePath), expense({ amountMinor: -1 })));
  await assertFails(setDoc(doc(ownerDb, expensePath), expense({ currencyCode: "eur" })));
  await assertFails(setDoc(doc(ownerDb, expensePath), expense({ currencyExponent: 9 })));
  await assertFails(setDoc(doc(ownerDb, expensePath), expense({ periodStartEpochDay: 5, periodEndEpochDay: 4 })));
  await assertFails(deleteDoc(doc(ownerDb, expensePath)));

  await assertSucceeds(setDoc(doc(ownerDb, reminderPath), reminder()));
  await assertFails(setDoc(doc(ownerDb, reminderPath), reminder({ dueEpochDay: null })));
  await assertFails(setDoc(doc(ownerDb, reminderPath), reminder({ recurrenceMonths: 0 })));
  await assertFails(setDoc(doc(ownerDb, reminderPath), reminder({ status: "SNOOZED", snoozedUntilEpochDay: null })));
  await assertFails(deleteDoc(doc(ownerDb, reminderPath)));

  const unknownPath = `users/${uid}/vehicleExpenses/expense-future`;
  await environment.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), unknownPath), expense({
      expenseId: "expense-future",
      futureField: "preserve-me"
    }));
  });
  await assertSucceeds(setDoc(doc(ownerDb, unknownPath), {
    ...expense({
      expenseId: "expense-future",
      revision: 2,
      operationId: operation2,
      clientUpdatedAt: 2
    })
  }, { merge: true }));
  const preserved = await getDoc(doc(ownerDb, unknownPath));
  assert.equal(preserved.data().futureField, "preserve-me");
} finally {
  await environment.cleanup();
}
