const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create and read their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      deviceName: "Pixel 8",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot read another user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const now = new Date();
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      deviceName: "Pixel 8",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create their own transfer record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transfers").doc("tx_001").set({
      id: "tx_001",
      userId: ALICE_UID,
      fileName: "photo.jpg",
      fileSize: 1024,
      fileType: "PHOTOS",
      direction: "SEND",
      peerName: "Bob Phone",
      status: "COMPLETED",
      createdAt: now,
    })
  );
});

test("Authenticated user: cannot create transfer record with invalid status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transfers").doc("tx_002").set({
      id: "tx_002",
      userId: ALICE_UID,
      fileName: "photo.jpg",
      fileSize: 1024,
      fileType: "PHOTOS",
      direction: "SEND",
      peerName: "Bob Phone",
      status: "INVALID_STATUS",
      createdAt: now,
    })
  );
});

test("Cross-user: Bob cannot read or create Alice transfers", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transfers").doc("tx_003").set({
      id: "tx_003",
      userId: ALICE_UID,
      fileName: "hack.exe",
      fileSize: 100,
      fileType: "FILES",
      direction: "RECEIVE",
      peerName: "Malicious",
      status: "COMPLETED",
      createdAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transfers").get());
});
