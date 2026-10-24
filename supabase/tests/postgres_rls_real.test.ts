// Mágina Olivo — G0 Real PostgreSQL & RLS Integration Test Suite
// Executes against real PostgreSQL 18.3 engine (PGlite) in an isolated local environment.
// Tests all 4 actors: User A (Owner), User B (Exploiter), User C (Worker), User D (Intruder)
// Run: node --experimental-strip-types --test supabase/tests/postgres_rls_real.test.ts

import { test, before } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { createHash } from "node:crypto";

import { pathToFileURL, fileURLToPath } from "node:url";

// Dynamically resolve PGlite from local package, supabase/node_modules, or scratch/node_modules
let PGlite: any;
const candidatePaths = [
  "@electric-sql/pglite",
  join(process.cwd(), "supabase/node_modules/@electric-sql/pglite/dist/index.js"),
  join(process.cwd(), "scratch/node_modules/@electric-sql/pglite/dist/index.js"),
  join(process.cwd(), "node_modules/@electric-sql/pglite/dist/index.js"),
  "../../scratch/node_modules/@electric-sql/pglite/dist/index.js"
];

for (const p of candidatePaths) {
  try {
    const importTarget = p.startsWith("@") || p.startsWith(".") ? p : pathToFileURL(p).href;
    const mod = await import(importTarget);
    PGlite = mod.PGlite;
    if (PGlite) break;
  } catch {
    // continue
  }
}

if (!PGlite) {
  throw new Error(
    "Could not resolve @electric-sql/pglite. Please run 'npm install --prefix supabase' or ensure package is in scratch/node_modules."
  );
}

const USER_A = "a0000000-0000-0000-0000-000000000001"; // Propietario (Owner)
const USER_B = "b0000000-0000-0000-0000-000000000002"; // Arrendatario / Explotador (Exploiter)
const USER_C = "c0000000-0000-0000-0000-000000000003"; // Trabajador de campo (Worker)
const USER_D = "d0000000-0000-0000-0000-000000000004"; // Intruso no autorizado (Intruder)

let db: any;
let workspaceAId: string;
let farmId: string;
let farm2Id: string;
let campaignId: string;
let contractId: string;
let agreementId: string;
let deliveryId: string;

function hashToken(plaintext: string): string {
  return createHash("sha256").update(plaintext).digest("hex");
}

async function asUser<T>(userId: string, fn: () => Promise<T>): Promise<T> {
  await db.exec(`SET ROLE authenticated;`);
  await db.exec(`SET request.jwt.claim.sub = '${userId}';`);
  try {
    return await fn();
  } finally {
    await db.exec(`RESET ROLE;`);
    await db.exec(`SET request.jwt.claim.sub = '';`);
  }
}

async function asSuperuser<T>(fn: () => Promise<T>): Promise<T> {
  await db.exec(`RESET ROLE;`);
  await db.exec(`SET request.jwt.claim.sub = '';`);
  return await fn();
}

before(async () => {
  db = new PGlite();
  const rootDir = fileURLToPath(new URL("../../", import.meta.url));

  // 1. Run mock environment fixture (emulating native Supabase auth and storage platform objects)
  const mockEnv = readFileSync(
    join(rootDir, "supabase/tests/fixtures/mock_supabase_environment.sql"),
    "utf8"
  );
  await db.exec(mockEnv);
  await db.exec(`CREATE ROLE anon NOSUPERUSER NOCREATEDB NOCREATEROLE;`);

  // 2. Run pure production migrations in order
  const mig1 = readFileSync(
    join(rootDir, "supabase/migrations/20261011120000_g0_auth_workspaces_shared_farms.sql"),
    "utf8"
  );
  const mig2 = readFileSync(
    join(rootDir, "supabase/migrations/20261011120001_g0_agricultural_sync_schema.sql"),
    "utf8"
  );
  const mig3 = readFileSync(
    join(rootDir, "supabase/migrations/20261011120002_g0_contracts_and_storage_rls.sql"),
    "utf8"
  );

  await db.exec(mig1);
  await db.exec(mig2);
  await db.exec(mig3);
  await db.exec(readFileSync(join(rootDir, 'supabase/migrations/20261016041222_g0_legacy_storage_parcel_hardening.sql'), 'utf8'));
  await db.exec(readFileSync(join(rootDir, 'supabase/migrations/20261016041601_g0_aggregate_identity_guards.sql'), 'utf8'));
  await db.exec(readFileSync(join(rootDir, 'supabase/migrations/20261024125046_g0_function_security_hardening.sql'), 'utf8'));

  // 3. Grant privileges to authenticated role
  await db.exec(`
    GRANT USAGE ON SCHEMA public TO authenticated;
    GRANT USAGE ON SCHEMA auth TO authenticated;
    GRANT USAGE ON SCHEMA storage TO authenticated;
    GRANT ALL ON ALL TABLES IN SCHEMA public TO authenticated;
    GRANT ALL ON ALL TABLES IN SCHEMA storage TO authenticated;
    GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO authenticated;
    GRANT EXECUTE ON ALL ROUTINES IN SCHEMA public TO authenticated;
    GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated;

    -- Seed 4 auth.users
    INSERT INTO auth.users (id, email) VALUES
      ('${USER_A}', 'propietario.a@magina.es'),
      ('${USER_B}', 'explotador.b@magina.es'),
      ('${USER_C}', 'trabajador.c@magina.es'),
      ('${USER_D}', 'intruso.d@magina.es')
    ON CONFLICT (id) DO NOTHING;
  `);

  // 4. Set up base data as superuser, then test RLS from authenticated perspective
  // Create workspace owned by User A
  const wsRes = await db.query(
    `INSERT INTO public.workspaces (owner_user_id, name)
     VALUES ('${USER_A}', 'Explotación Hermanos A (Bedmar)')
     RETURNING id;`
  );
  workspaceAId = wsRes.rows[0].id;

  // Create Farm Las Lagunillas owned by User A
  const farmRes = await db.query(
    `INSERT INTO public.farms (workspace_id, creator_user_id, owner_id, name, municipality, province)
     VALUES ('${workspaceAId}', '${USER_A}', '${USER_A}', 'Finca Las Lagunillas', 'Bedmar y Garcíez', 'Jaén')
     RETURNING id;`
  );
  farmId = farmRes.rows[0].id;

  // Create second farm for cross-farm FK test
  const farm2Res = await db.query(
    `INSERT INTO public.farms (workspace_id, creator_user_id, owner_id, name, municipality)
     VALUES ('${workspaceAId}', '${USER_A}', '${USER_A}', 'Finca El Cerrajón', 'Jódar')
     RETURNING id;`
  );
  farm2Id = farm2Res.rows[0].id;

  // Create campaign for Farm 1
  const campRes = await db.query(
    `INSERT INTO public.campaigns (workspace_id, farm_id, name, start_date)
     VALUES ('${workspaceAId}', '${farmId}', 'Campaña 2026/2027 Bedmar', '2026-10-01')
     RETURNING id;`
  );
  campaignId = campRes.rows[0].id;
});

test('G0 privileged RPCs reject anonymous execution and trigger paths are fixed', async () => {
  const routines = [
    'accept_farm_contract(uuid)',
    'accept_harvest_share_agreement(uuid)',
    'claim_farm_invitation(text)',
    'confirm_farm_invitation(uuid)',
    'has_farm_capability(uuid,text)',
    'user_accessible_farm_ids()',
  ];
  for (const routine of routines) {
    const privilege = await db.query(`SELECT has_function_privilege('anon', 'public.${routine}', 'EXECUTE') AS anon_exec,
      has_function_privilege('authenticated', 'public.${routine}', 'EXECUTE') AS auth_exec`);
    assert.equal(privilege.rows[0].anon_exec, false, routine);
    assert.equal(privilege.rows[0].auth_exec, true, routine);
  }
  const paths = await db.query(`SELECT proname, proconfig FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND proname IN ('check_farm_principal_immutability',
      'deny_delivery_hard_delete', 'check_contract_acceptance_integrity',
      'check_share_agreement_acceptance_integrity')`);
  assert.equal(paths.rows.length, 4);
  for (const routine of paths.rows) {
    assert.deepEqual(routine.proconfig, ['search_path=pg_catalog, public'], routine.proname);
  }
});

test("RLS 1: User A (Owner) sees farm; User D (Intruder) sees nothing", async () => {
  const aFarms = await asUser(USER_A, async () => {
    return await db.query(`SELECT id, name FROM public.farms WHERE id = '${farmId}';`);
  });
  assert.equal(aFarms.rows.length, 1);
  assert.equal(aFarms.rows[0].name, "Finca Las Lagunillas");

  const dFarms = await asUser(USER_D, async () => {
    return await db.query(`SELECT id, name FROM public.farms;`);
  });
  assert.equal(dFarms.rows.length, 0);
});

test("RLS 2: User D cannot insert farm into User A's workspace", async () => {
  await assert.rejects(
    asUser(USER_D, async () => {
      await db.query(
        `INSERT INTO public.farms (workspace_id, creator_user_id, name)
         VALUES ('${workspaceAId}', '${USER_D}', 'Finca Hackeada');`
      );
    }),
    /violates row-level security policy/i
  );
});

test("RLS 3: Secure QR Invitation Claim & Replay Prevention (P0-2)", async () => {
  const tokenPlaintext = "TOKEN-QR-BEDMAR-EXPLOITER-777";
  const tokenHash = hashToken(tokenPlaintext);
  const expiresAt = new Date(Date.now() + 86400000).toISOString();

  await asSuperuser(async () => {
    await db.exec(`SET request.jwt.claim.sub = '${USER_A}';`);
    await db.query(
      `INSERT INTO public.farm_invitations
         (farm_id, issued_by_user_id, token_hash, role, capabilities, expires_at, max_uses, invitee_email_hash)
       VALUES
         ('${farmId}', '${USER_A}', '${tokenHash}', 'EXPLOITER',
          ARRAY['view_farm', 'add_deliveries', 'view_deliveries', 'view_contracts', 'upload_contracts', 'manage_agreements'],
          '${expiresAt}', 1, '${hashToken('explotador.b@magina.es')}');`
    );
    await db.exec(`SET request.jwt.claim.sub = '';`);
  });

  const dInvs = await asUser(USER_D, async () => {
    return await db.query(`SELECT * FROM public.farm_invitations;`);
  });
  assert.equal(dInvs.rows.length, 0);

  const claimRes = await asUser(USER_B, async () => {
    const res = await db.query(`SELECT public.claim_farm_invitation('${tokenPlaintext}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(claimRes.success, true);
  assert.equal(claimRes.farm_id, farmId);
  assert.equal(claimRes.role, "EXPLOITER");
  assert.equal(claimRes.status, "ACCEPTED");

  const replayRes = await asUser(USER_D, async () => {
    const res = await db.query(`SELECT public.claim_farm_invitation('${tokenPlaintext}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(replayRes.success, false);
  assert.equal(replayRes.error, "invitation_invalid_or_expired");

  const bFarms = await asUser(USER_B, async () => {
    return await db.query(`SELECT id, name FROM public.farms WHERE id = '${farmId}';`);
  });
  assert.equal(bFarms.rows.length, 1);

  const bGrants = await asUser(USER_B, async () => {
    return await db.query(`SELECT role, status FROM public.farm_access_grants WHERE farm_id = '${farmId}';`);
  });
  assert.equal(bGrants.rows.length, 1);
  assert.equal(bGrants.rows[0].role, "EXPLOITER");
  assert.equal(bGrants.rows[0].status, "ACTIVE");
});

test("RLS 4: Worker C Invitation with Restricted Capabilities", async () => {
  const tokenPlaintext = "TOKEN-QR-BEDMAR-WORKER-888";
  const tokenHash = hashToken(tokenPlaintext);
  const expiresAt = new Date(Date.now() + 86400000).toISOString();

  await asSuperuser(async () => {
    await db.exec(`SET request.jwt.claim.sub = '${USER_A}';`);
    await db.query(
      `INSERT INTO public.farm_invitations
         (farm_id, issued_by_user_id, token_hash, role, capabilities, expires_at, max_uses, invitee_email_hash)
       VALUES
         ('${farmId}', '${USER_A}', '${tokenHash}', 'WORKER',
          ARRAY['view_farm', 'add_deliveries'],
          '${expiresAt}', 1, '${hashToken('trabajador.c@magina.es')}');`
    );
    await db.exec(`SET request.jwt.claim.sub = '';`);
  });

  const claimRes = await asUser(USER_C, async () => {
    const res = await db.query(`SELECT public.claim_farm_invitation('${tokenPlaintext}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(claimRes.success, true);
  assert.equal(claimRes.role, "WORKER");

  const cFarms = await asUser(USER_C, async () => {
    return await db.query(`SELECT id, name FROM public.farms WHERE id = '${farmId}';`);
  });
  assert.equal(cFarms.rows.length, 1);
});

test("RLS 5: Composite FK Prevents Cross-Farm Delivery (P0-7)", async () => {
  await assert.rejects(
    asSuperuser(async () => {
      await db.query(
        `INSERT INTO public.deliveries
           (workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, recorded_by_user_id)
         VALUES
           ('${workspaceAId}', '${farm2Id}', '${campaignId}', '2026-10-11', 'SCA Bedmar', 1200000, '${USER_A}');`
      );
    }),
    /violates foreign key constraint "fk_deliveries_campaign_farm"/i
  );
});

test("RLS 6: Exploiter B records 1.200 kg delivery; Owner A reads it; Intruder D gets nothing", async () => {
  const delivRes = await asUser(USER_B, async () => {
    return await db.query(
      `INSERT INTO public.deliveries
         (workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, ticket_number, recorded_by_user_id)
       VALUES
         ('${workspaceAId}', '${farmId}', '${campaignId}', '2026-10-11', 'SCA Bedmar y Garcíez', 1200000, 'TK-BED-001', '${USER_B}')
       RETURNING id, net_grams, version;`
    );
  });
  deliveryId = delivRes.rows[0].id;
  assert.ok(deliveryId);
  assert.equal(Number(delivRes.rows[0].net_grams), 1200000);
  assert.equal(Number(delivRes.rows[0].version), 1);

  const aDeliv = await asUser(USER_A, async () => {
    return await db.query(`SELECT id, net_grams, ticket_number FROM public.deliveries WHERE id = '${deliveryId}';`);
  });
  assert.equal(aDeliv.rows.length, 1);
  assert.equal(aDeliv.rows[0].ticket_number, "TK-BED-001");

  const dDeliv = await asUser(USER_D, async () => {
    return await db.query(`SELECT id FROM public.deliveries;`);
  });
  assert.equal(dDeliv.rows.length, 0);
});

test("RLS 7: Delivery Immutability & Soft-Delete Enforcement (P0-8)", async () => {
  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `UPDATE public.deliveries SET farm_id = gen_random_uuid() WHERE id = '${deliveryId}';`
      );
    }),
    /Cannot alter farm_id or campaign_id of an existing delivery/i
  );

  const delAttempt = await asUser(USER_A, async () => {
    return await db.query(`DELETE FROM public.deliveries WHERE id = '${deliveryId}';`);
  });
  assert.equal(delAttempt.affectedRows, 0);

  await assert.rejects(
    asSuperuser(async () => {
      await db.query(`DELETE FROM public.deliveries WHERE id = '${deliveryId}';`);
    }),
    /Hard delete forbidden on deliveries/i
  );

  const checkDelivery = await asUser(USER_A, async () => {
    return await db.query(`SELECT id FROM public.deliveries WHERE id = '${deliveryId}';`);
  });
  assert.equal(checkDelivery.rows.length, 1);

  const softDelRes = await asUser(USER_A, async () => {
    return await db.query(
      `UPDATE public.deliveries SET deleted_at = now() WHERE id = '${deliveryId}' RETURNING version, deleted_at;`
    );
  });
  assert.equal(softDelRes.rows.length, 1);
  assert.ok(parseInt(softDelRes.rows[0].version) >= 2);
  assert.ok(softDelRes.rows[0].deleted_at);

  await asSuperuser(async () => {
    await db.query(`UPDATE public.deliveries SET deleted_at = NULL WHERE id = '${deliveryId}';`);
  });
});

test("RLS 8: Bilateral Farm Contract & 70/30 Harvest Share Agreement (P0-4, P0-5)", async () => {
  const contractRes = await asUser(USER_A, async () => {
    return await db.query(
      `INSERT INTO public.farm_contracts
         (farm_id, contract_type, start_date, end_date, owner_user_id, exploiter_user_id, terms_notes)
       VALUES
         ('${farmId}', 'LEASE', '2026-10-01', '2031-09-30', '${USER_A}', '${USER_B}', 'Arrendamiento rústico 5 campañas')
       RETURNING id;`
    );
  });
  contractId = contractRes.rows[0].id;
  assert.ok(contractId);

  const aAccept = await asUser(USER_A, async () => {
    const res = await db.query(`SELECT public.accept_farm_contract('${contractId}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(aAccept.success, true);
  assert.equal(aAccept.status, "PROPOSED");
  assert.ok(aAccept.accepted_by_owner_at);
  assert.equal(aAccept.accepted_by_exploiter_at, null);

  const dAccept = await asUser(USER_D, async () => {
    const res = await db.query(`SELECT public.accept_farm_contract('${contractId}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(dAccept.success, false);
  assert.equal(dAccept.error, "unauthorized_not_a_contract_party");

  const bAccept = await asUser(USER_B, async () => {
    const res = await db.query(`SELECT public.accept_farm_contract('${contractId}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(bAccept.success, true);
  assert.equal(bAccept.status, "ACCEPTED");
  assert.ok(bAccept.accepted_by_owner_at);
  assert.ok(bAccept.accepted_by_exploiter_at);

  const agrRes = await asUser(USER_A, async () => {
    return await db.query(
      `INSERT INTO public.campaign_harvest_share_agreements
         (farm_id, campaign_id, contract_id, owner_user_id, exploiter_user_id, owner_percentage, exploiter_percentage, version)
       VALUES
         ('${farmId}', '${campaignId}', '${contractId}', '${USER_A}', '${USER_B}', 30.00, 70.00, 1)
       RETURNING id, owner_percentage, exploiter_percentage;`
    );
  });
  agreementId = agrRes.rows[0].id;
  assert.equal(agrRes.rows[0].owner_percentage, "30.00");
  assert.equal(agrRes.rows[0].exploiter_percentage, "70.00");

  await asUser(USER_A, async () => {
    await db.query(`SELECT public.accept_harvest_share_agreement('${agreementId}');`);
  });
  const bAgrAccept = await asUser(USER_B, async () => {
    const res = await db.query(`SELECT public.accept_harvest_share_agreement('${agreementId}') AS result;`);
    return res.rows[0].result;
  });
  assert.equal(bAgrAccept.success, true);
  assert.equal(bAgrAccept.status, "ACCEPTED");

  const cContracts = await asUser(USER_C, async () => {
    return await db.query(`SELECT * FROM public.farm_contracts WHERE id = '${contractId}';`);
  });
  assert.equal(cContracts.rows.length, 0);

  const dContracts = await asUser(USER_D, async () => {
    return await db.query(`SELECT * FROM public.farm_contracts;`);
  });
  assert.equal(dContracts.rows.length, 0);

  const totalNetGrams = 10000000;
  const exploiterGrams = Math.round((totalNetGrams * 70) / 100);
  const ownerGrams = Math.round((totalNetGrams * 30) / 100);
  assert.equal(exploiterGrams, 7000000);
  assert.equal(ownerGrams, 3000000);
  assert.equal(exploiterGrams + ownerGrams, totalNetGrams);
});

test("RLS 9: Direct Tampering of Contract Acceptance Integrity is Blocked by Triggers", async () => {
  // Create a proposed contract
  const newContractRes = await asUser(USER_A, async () => {
    return await db.query(
      `INSERT INTO public.farm_contracts
         (farm_id, contract_type, start_date, end_date, owner_user_id, exploiter_user_id)
       VALUES
         ('${farmId}', 'SHARECROPPING', '2026-10-01', '2027-09-30', '${USER_A}', '${USER_B}')
       RETURNING id;`
    );
  });
  const testContractId = newContractRes.rows[0].id;

  // Owner A attempts to directly forge accepted_by_exploiter_at on contract: BLOCKED by trigger
  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `UPDATE public.farm_contracts
         SET accepted_by_exploiter_at = now()
         WHERE id = '${testContractId}';`
      );
    }),
    /Only the exploiter can accept for the exploiter/i
  );

  // User A attempts to forge status to ACCEPTED without exploiter's acceptance: BLOCKED
  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `UPDATE public.farm_contracts
         SET status = 'ACCEPTED'
         WHERE id = '${testContractId}';`
      );
    }),
    /Contract cannot be ACCEPTED without bilateral acceptance timestamps/i
  );

  // Direct forgery on campaign harvest share agreement status without bilateral timestamps: BLOCKED
  const newAgrRes = await asUser(USER_A, async () => {
    return await db.query(
      `INSERT INTO public.campaign_harvest_share_agreements
         (farm_id, campaign_id, owner_user_id, exploiter_user_id, owner_percentage, exploiter_percentage, version)
       VALUES
         ('${farmId}', '${campaignId}', '${USER_A}', '${USER_B}', 30.00, 70.00, 2)
       RETURNING id;`
    );
  });
  const testAgrId = newAgrRes.rows[0].id;

  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `UPDATE public.campaign_harvest_share_agreements
         SET status = 'ACCEPTED'
         WHERE id = '${testAgrId}';`
      );
    }),
    /Share agreement cannot be ACCEPTED without bilateral acceptance timestamps/i
  );

  // TAREA 2: Direct INSERT with status = 'ACCEPTED' and pre-filled signatures: BLOCKED
  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `INSERT INTO public.farm_contracts
           (farm_id, contract_type, start_date, end_date, owner_user_id, exploiter_user_id, status, accepted_by_owner_at, accepted_by_exploiter_at)
         VALUES
           ('${farmId}', 'LEASE', '2026-10-01', '2031-09-30', '${USER_A}', '${USER_B}', 'ACCEPTED', now(), now());`
      );
    }),
    /Contract cannot be directly created with status ACCEPTED/i
  );

  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `INSERT INTO public.campaign_harvest_share_agreements
           (farm_id, campaign_id, owner_user_id, exploiter_user_id, owner_percentage, exploiter_percentage, version, status, accepted_by_owner_at, accepted_by_exploiter_at)
         VALUES
           ('${farmId}', '${campaignId}', '${USER_A}', '${USER_B}', 30.00, 70.00, 3, 'ACCEPTED', now(), now());`
      );
    }),
    /Harvest share agreement cannot be directly created with status ACCEPTED/i
  );

  // TAREA 3: Modifying terms/percentages after bilateral ACCEPTANCE: BLOCKED
  // contractId from RLS 8 is already in status 'ACCEPTED'
  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `UPDATE public.farm_contracts
         SET start_date = '2026-11-01'
         WHERE id = '${contractId}';`
      );
    }),
    /Cannot modify conditions of an already ACCEPTED contract/i
  );

  // agreementId from RLS 8 is already in status 'ACCEPTED' with 30/70 split
  await assert.rejects(
    asUser(USER_A, async () => {
      await db.query(
        `UPDATE public.campaign_harvest_share_agreements
         SET owner_percentage = 50.00, exploiter_percentage = 50.00
         WHERE id = '${agreementId}';`
      );
    }),
    /Cannot modify percentages of an already ACCEPTED agreement/i
  );
});

async function freshContract(signers = [USER_A, USER_B]) {
  const row = await asUser(USER_A, () => db.query(`
    INSERT INTO public.farm_contracts
      (farm_id, start_date, end_date, owner_user_id, exploiter_user_id, terms_notes, cost_responsibilities)
    VALUES ('${farmId}', '2026-10-01', '2027-09-30', '${USER_A}', '${USER_B}', 'Agreed terms', '{"fuel":"exploiter"}') RETURNING id;`));
  const id = row.rows[0].id;
  for (const actor of signers) {
    const result = await asUser(actor, () => db.query(`SELECT public.accept_farm_contract('${id}') AS result;`));
    assert.equal(result.rows[0].result.success, true);
  }
  return id;
}

let agreementVersion = 100;
async function freshAgreement(signers = [USER_A, USER_B]) {
  const row = await asUser(USER_A, () => db.query(`
    INSERT INTO public.campaign_harvest_share_agreements
      (farm_id, campaign_id, contract_id, owner_user_id, exploiter_user_id, version)
    VALUES ('${farmId}', '${campaignId}', '${contractId}', '${USER_A}', '${USER_B}', ${agreementVersion++}) RETURNING id;`));
  const id = row.rows[0].id;
  for (const actor of signers) {
    const result = await asUser(actor, () => db.query(`SELECT public.accept_harvest_share_agreement('${id}') AS result;`));
    assert.equal(result.rows[0].result.success, true);
  }
  return id;
}

for (const [field, value] of [
  ["terms_notes", "'Changed after signing'"],
  ["cost_responsibilities", "'{\"fuel\":\"owner\"}'::jsonb"],
  ["duration_years", "99"],
]) {
  test(`P0: accepted contract freezes ${field}`, async () => {
    const id = await freshContract();
    await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.farm_contracts SET ${field}=${value} WHERE id='${id}';`)), /conditions|signed/i);
  });
}

test("P0: ACCEPTED -> PROPOSED -> changed contract cannot reuse signatures", async () => {
  const id = await freshContract();
  await assert.rejects(asUser(USER_A, async () => {
    await db.query(`UPDATE public.farm_contracts SET status='PROPOSED' WHERE id='${id}';`);
    await db.query(`UPDATE public.farm_contracts SET start_date='2026-11-01', status='ACCEPTED' WHERE id='${id}';`);
  }), /status|signed|downgrade/i);
  const row = await asUser(USER_A, () => db.query(`SELECT status FROM public.farm_contracts WHERE id='${id}';`));
  assert.equal(row.rows[0].status, "ACCEPTED");
});

test("P0: ACCEPTED -> PROPOSED -> 50/50 agreement cannot reuse signatures", async () => {
  const id = await freshAgreement();
  await assert.rejects(asUser(USER_A, async () => {
    await db.query(`UPDATE public.campaign_harvest_share_agreements SET status='PROPOSED' WHERE id='${id}';`);
    await db.query(`UPDATE public.campaign_harvest_share_agreements SET owner_percentage=50, exploiter_percentage=50, status='ACCEPTED' WHERE id='${id}';`);
  }), /status|signed|downgrade/i);
  const row = await asUser(USER_A, () => db.query(`SELECT status, owner_percentage FROM public.campaign_harvest_share_agreements WHERE id='${id}';`));
  assert.equal(row.rows[0].status, "ACCEPTED");
  assert.equal(row.rows[0].owner_percentage, "30.00");
});

test("P0: first contract signature freezes terms before the second consent", async () => {
  const id = await freshContract([USER_A]);
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.farm_contracts SET terms_notes='Different bargain' WHERE id='${id}';`)), /conditions|signed/i);
});

test("P0: first agreement signature freezes percentages and negotiated version", async () => {
  const id = await freshAgreement([USER_A]);
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.campaign_harvest_share_agreements SET owner_percentage=50, exploiter_percentage=50 WHERE id='${id}';`)), /percentages|signed/i);
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.campaign_harvest_share_agreements SET version=999 WHERE id='${id}';`)), /version|signed/i);
});

test("P1: own contract signature must also use the RPC", async () => {
  const id = await freshContract([]);
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.farm_contracts SET accepted_by_owner_at=now() WHERE id='${id}';`)), /RPC/i);
});

test("P1: own agreement signature must also use the RPC", async () => {
  const id = await freshAgreement([]);
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.campaign_harvest_share_agreements SET accepted_by_owner_at=now() WHERE id='${id}';`)), /RPC/i);
});

test("P0: temporary catalog shadow cannot impersonate the trusted acceptance RPC", async () => {
  const contract = await freshContract([]);
  const agreement = await freshAgreement([]);
  await asSuperuser(() => db.exec(`DO $$ BEGIN EXECUTE format('GRANT TEMPORARY ON DATABASE %I TO authenticated', current_database()); END $$;`));
  await asUser(USER_A, async () => {
    await db.exec(`CREATE TEMP TABLE pg_proc (oid oid, proowner oid);
      INSERT INTO pg_temp.pg_proc SELECT oid, 'authenticated'::regrole::oid FROM pg_catalog.pg_proc
      WHERE oid IN ('public.accept_farm_contract(uuid)'::regprocedure, 'public.accept_harvest_share_agreement(uuid)'::regprocedure);`);
    try {
      await assert.rejects(db.query(`UPDATE public.farm_contracts SET accepted_by_owner_at=now() WHERE id='${contract}';`), /RPC/i);
      await assert.rejects(db.query(`UPDATE public.campaign_harvest_share_agreements SET accepted_by_owner_at=now() WHERE id='${agreement}';`), /RPC/i);
    } finally {
      await db.exec(`DROP TABLE pg_temp.pg_proc;`);
    }
  });
});

test("P1: contract INSERT cannot carry a prefilled own signature", async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.farm_contracts
    (farm_id, start_date, end_date, owner_user_id, exploiter_user_id, accepted_by_owner_at)
    VALUES ('${farmId}', '2026-10-01', '2027-09-30', '${USER_A}', '${USER_B}', now());`)), /RPC|pre-filled|timestamps/i);
});

test("P0: the same identity cannot provide both contract consents", async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.farm_contracts
    (farm_id, start_date, end_date, owner_user_id, exploiter_user_id)
    VALUES ('${farmId}', '2026-10-01', '2027-09-30', '${USER_A}', '${USER_A}');`)), /distinct|different/i);
});

test("P0: signed rows cannot be hard deleted", async () => {
  const id = await freshContract();
  const agreement = await freshAgreement();
  await assert.rejects(asUser(USER_A, () => db.query(`DELETE FROM public.farm_contracts WHERE id='${id}';`)), /signed|delete/i);
  await assert.rejects(asUser(USER_A, () => db.query(`DELETE FROM public.campaign_harvest_share_agreements WHERE id='${agreement}';`)), /signed|delete/i);
});

test("RLS 10: Storage RLS verifying Contract Parties (P0-3)", async () => {
  const contractPdfPath = `${farmId}/${contractId}/contrato_arrendamiento_2026.pdf`;

  await asSuperuser(async () => {
    await db.query(
      `INSERT INTO storage.objects (bucket_id, name, owner)
       VALUES ('farm-contracts', '${contractPdfPath}', '${USER_A}');`
    );
  });

  const aStorage = await asUser(USER_A, async () => {
    return await db.query(
      `SELECT name FROM storage.objects WHERE bucket_id = 'farm-contracts' AND name = '${contractPdfPath}';`
    );
  });
  assert.equal(aStorage.rows.length, 1);

  const bStorage = await asUser(USER_B, async () => {
    return await db.query(
      `SELECT name FROM storage.objects WHERE bucket_id = 'farm-contracts' AND name = '${contractPdfPath}';`
    );
  });
  assert.equal(bStorage.rows.length, 1);

  const cStorage = await asUser(USER_C, async () => {
    return await db.query(
      `SELECT name FROM storage.objects WHERE bucket_id = 'farm-contracts' AND name = '${contractPdfPath}';`
    );
  });
  assert.equal(cStorage.rows.length, 0);

  const dStorage = await asUser(USER_D, async () => {
    return await db.query(
      `SELECT name FROM storage.objects WHERE bucket_id = 'farm-contracts' AND name = '${contractPdfPath}';`
    );
  });
  assert.equal(dStorage.rows.length, 0);
});

test("RLS 11: Grant Revocation Instantly Closes Access", async () => {
  const bDelivBefore = await asUser(USER_B, async () => {
    return await db.query(`SELECT id FROM public.deliveries WHERE farm_id = '${farmId}';`);
  });
  assert.ok(bDelivBefore.rows.length > 0);

  await asSuperuser(async () => {
    await db.query(
      `UPDATE public.farm_access_grants
       SET status = 'REVOKED', updated_at = now()
       WHERE farm_id = '${farmId}' AND user_id = '${USER_B}';`
    );
  });

  const bDelivAfter = await asUser(USER_B, async () => {
    return await db.query(`SELECT id FROM public.deliveries WHERE farm_id = '${farmId}';`);
  });
  assert.equal(bDelivAfter.rows.length, 0);

  await assert.rejects(
    asUser(USER_B, async () => {
      await db.query(
        `INSERT INTO public.deliveries
           (workspace_id, farm_id, campaign_id, delivery_date, destination_name, net_grams, recorded_by_user_id)
         VALUES
           ('${workspaceAId}', '${farmId}', '${campaignId}', '2026-10-12', 'SCA Bedmar', 500000, '${USER_B}');`
      );
    }),
    /violates row-level security policy/i
  );
  const revokedAccept = await asUser(USER_B, () => db.query(`SELECT public.accept_farm_contract('${contractId}') AS result;`));
  assert.equal(revokedAccept.rows[0].result.success, false, "SECURITY DEFINER must respect revoked contract access");
  const revokedShare = await asUser(USER_B, () => db.query(`SELECT public.accept_harvest_share_agreement('${agreementId}') AS result;`));
  assert.equal(revokedShare.rows[0].result.success, false, "SECURITY DEFINER must respect revoked agreement access");
});

test("P0: agreement cannot link another farm's campaign", async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.campaign_harvest_share_agreements
    (farm_id, campaign_id, owner_user_id, exploiter_user_id, version)
    VALUES ('${farm2Id}', '${campaignId}', '${USER_A}', '${USER_B}', 700);`)), /foreign key|campaign.*farm/i);
});

test("P0: agreement cannot link a contract with different parties", async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.campaign_harvest_share_agreements
    (farm_id, campaign_id, contract_id, owner_user_id, exploiter_user_id, version)
    VALUES ('${farmId}', '${campaignId}', '${contractId}', '${USER_A}', '${USER_C}', 701);`)), /foreign key|contract.*parties/i);
});

test("P0: delivery cannot claim a workspace unrelated to its farm", async () => {
  const other = await asUser(USER_D, () => db.query(`INSERT INTO public.workspaces (owner_user_id,name) VALUES ('${USER_D}','Isolated intruder workspace') RETURNING id;`));
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.deliveries
    (workspace_id,farm_id,campaign_id,delivery_date,destination_name,net_grams,recorded_by_user_id)
    VALUES ('${other.rows[0].id}','${farmId}','${campaignId}','2026-10-12','QA',1000,'${USER_A}');`)), /workspace|foreign key/i);
});

test("P0: harvest cannot use another farm's campaign", async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.harvests
    (workspace_id,farm_id,campaign_id,harvest_date,weight_grams,recorded_by_user_id)
    VALUES ('${workspaceAId}','${farm2Id}','${campaignId}','2026-10-12',1000,'${USER_A}');`)), /campaign.*farm|foreign key/i);
});

test("P0: delegated manage_farm cannot promote itself to creator/owner", async () => {
  const isolated = await asSuperuser(() => db.query(`INSERT INTO public.farms (workspace_id,creator_user_id,owner_id,name)
    VALUES ('${workspaceAId}','${USER_A}','${USER_A}','Isolated delegated farm') RETURNING id;`));
  const id = isolated.rows[0].id;
  await asUser(USER_A, () => db.query(`INSERT INTO public.farm_access_grants (farm_id,user_id,granted_by_user_id,role,capabilities)
    VALUES ('${id}','${USER_B}','${USER_A}','EXPLOITER',ARRAY['view_farm','manage_farm']);`));
  await assert.rejects(asUser(USER_B, () => db.query(`UPDATE public.farms SET creator_user_id='${USER_B}',owner_id='${USER_B}' WHERE id='${id}';`)), /principal|owner|creator/i);
});

test("P0: grants starting in the future cannot expose a farm or capability", async () => {
  await asUser(USER_A, () => db.query(`INSERT INTO public.farm_access_grants (farm_id,user_id,granted_by_user_id,role,capabilities,valid_from)
    VALUES ('${farm2Id}','${USER_C}','${USER_A}','VIEWER',ARRAY['view_farm','view_deliveries'],now()+interval '1 day');`));
  const capability = await asUser(USER_C, () => db.query(`SELECT public.has_farm_capability('${farm2Id}','view_deliveries') AS allowed;`));
  assert.equal(capability.rows[0].allowed, false);
  const visible = await asUser(USER_C, () => db.query(`SELECT id FROM public.farms WHERE id='${farm2Id}';`));
  assert.equal(visible.rows.length, 0);
});

test("P0: email-bound invitation cannot be claimed by a different native identity", async () => {
  const isolated = await asSuperuser(() => db.query(`INSERT INTO public.farms (workspace_id,creator_user_id,owner_id,name)
    VALUES ('${workspaceAId}','${USER_A}','${USER_A}','Email-bound invitation farm') RETURNING id;`));
  const token = 'qa-email-bound-invite-token';
  await asUser(USER_A, () => db.query(`INSERT INTO public.farm_invitations
    (farm_id,issued_by_user_id,token_hash,invitee_email_hash,expires_at)
    VALUES ('${isolated.rows[0].id}','${USER_A}','${hashToken(token)}','${hashToken('explotador.b@magina.es')}',now()+interval '1 hour');`));
  const result = await asUser(USER_D, () => db.query(`SELECT public.claim_farm_invitation('${token}') AS result;`));
  assert.equal(result.rows[0].result.success, false);
});

test("P0: confirming an expired claimed invitation must not open access", async () => {
  const token = 'qa-expiring-claimed-invitation';
  const invitation = await asUser(USER_A, () => db.query(`INSERT INTO public.farm_invitations
    (farm_id,issued_by_user_id,token_hash,expires_at,requires_issuer_confirmation)
    VALUES ('${farm2Id}','${USER_A}','${hashToken(token)}',now()+interval '1 hour',true) RETURNING id;`));
  await asUser(USER_D, () => db.query(`SELECT public.claim_farm_invitation('${token}');`));
  await asUser(USER_A, () => db.query(`UPDATE public.farm_invitations SET expires_at=now()-interval '1 minute' WHERE id='${invitation.rows[0].id}';`));
  const result = await asUser(USER_A, () => db.query(`SELECT public.confirm_farm_invitation('${invitation.rows[0].id}') AS result;`));
  assert.equal(result.rows[0].result.success, false);
});

test("RLS: owner can create a farm through INSERT RETURNING", async () => {
  const created = await asUser(USER_A, () => db.query(`INSERT INTO public.farms (workspace_id,creator_user_id,owner_id,name)
    VALUES ('${workspaceAId}','${USER_A}','${USER_A}','Owner-created canonical farm') RETURNING id,name;`));
  assert.equal(created.rows.length, 1);
  assert.equal(created.rows[0].name, 'Owner-created canonical farm');
});

test("P0: unbound invitation waits for issuer confirmation even if caller disables the flag", async () => {
  const token = 'qa-unbound-invitation-token';
  const invitation = await asUser(USER_A, () => db.query(`INSERT INTO public.farm_invitations
    (farm_id,issued_by_user_id,token_hash,expires_at,requires_issuer_confirmation)
    VALUES ('${farm2Id}','${USER_A}','${hashToken(token)}',now()+interval '1 hour',false) RETURNING id;`));
  const result = await asUser(USER_B, () => db.query(`SELECT public.claim_farm_invitation('${token}') AS result;`));
  assert.equal(result.rows[0].result.status, 'CLAIMED');
  const before = await asUser(USER_B, () => db.query(`SELECT id FROM public.farms WHERE id='${farm2Id}';`));
  assert.equal(before.rows.length, 0);
  const confirmed = await asUser(USER_A, () => db.query(`SELECT public.confirm_farm_invitation('${invitation.rows[0].id}') AS result;`));
  assert.equal(confirmed.rows[0].result.success, true);
  const after = await asUser(USER_B, () => db.query(`SELECT id FROM public.farms WHERE id='${farm2Id}';`));
  assert.equal(after.rows.length, 1);
});

test('Audit: delivery author cannot be rewritten by the workspace owner', async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.deliveries SET recorded_by_user_id='${USER_D}' WHERE id='${deliveryId}';`)), /author|recorded_by/i);
});

test('Audit: expense cannot reference a delivery from another farm in the same workspace', async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`INSERT INTO public.expenses(workspace_id,farm_id,delivery_id,expense_date,concept,category,amount_minor)
    VALUES('${workspaceAId}','${farm2Id}','${deliveryId}',CURRENT_DATE,'Cross-farm fixture','OTHER',100);`)), /expense.*delivery|delivery.*farm/i);
});

test('Audit: document metadata cannot claim another workspace farmer aggregate', async () => {
  const other = await asUser(USER_D, () => db.query(`INSERT INTO public.workspaces(owner_user_id,name) VALUES('${USER_D}','Intruder own workspace') RETURNING id;`));
  await assert.rejects(asUser(USER_D, () => db.query(`INSERT INTO public.documents(workspace_id,owner_type,owner_id,type,mime_type,display_name)
    VALUES('${other.rows[0].id}','DELIVERY','${deliveryId}','PHOTO','image/jpeg','Forged');`)), /document.*owner|aggregate/i);
});

test('Audit: expired grant reinvitation cannot report acceptance without valid access', async () => {
  const token='qa-expired-grant-reinvite';
  await asSuperuser(() => db.exec(`UPDATE public.farm_access_grants SET valid_until=now()-interval '1 day' WHERE farm_id='${farm2Id}' AND user_id='${USER_B}';`));
  const invitation=await asUser(USER_A, () => db.query(`INSERT INTO public.farm_invitations(farm_id,issued_by_user_id,token_hash,expires_at,requires_issuer_confirmation)
    VALUES('${farm2Id}','${USER_A}','${hashToken(token)}',now()+interval '1 hour',true) RETURNING id;`));
  await asUser(USER_B, () => db.query(`SELECT public.claim_farm_invitation('${token}');`));
  await assert.rejects(asUser(USER_A, () => db.query(`SELECT public.confirm_farm_invitation('${invitation.rows[0].id}');`)), /grant.*valid|inactive grant/i);
  const state=await asSuperuser(() => db.query(`SELECT state FROM public.farm_invitations WHERE id='${invitation.rows[0].id}'`));
  assert.equal(state.rows[0].state,'CLAIMED');
});

test('Audit: delivery cannot reference a missing harvest', async () => {
  await assert.rejects(asUser(USER_A, () => db.query(`UPDATE public.deliveries SET harvest_id='99999999-0000-0000-0000-000000000999' WHERE id='${deliveryId}'`)), /foreign key|harvest/i);
});

test('Audit: valid expense and document retain original agricultural values', async () => {
  const expense=await asUser(USER_A, () => db.query(`INSERT INTO public.expenses(workspace_id,farm_id,campaign_id,delivery_id,expense_date,concept,category,amount_minor)
    VALUES('${workspaceAId}','${farmId}','${campaignId}','${deliveryId}',CURRENT_DATE,'Valid fixture','OTHER',12345) RETURNING amount_minor;`));
  assert.equal(Number(expense.rows[0].amount_minor),12345);
  const doc=await asUser(USER_A, () => db.query(`INSERT INTO public.documents(workspace_id,owner_type,owner_id,type,mime_type,display_name)
    VALUES('${workspaceAId}','DELIVERY','${deliveryId}','PHOTO','image/jpeg','Valid fixture') RETURNING owner_id;`));
  assert.equal(doc.rows[0].owner_id,deliveryId);
});

test('Audit: delivery cannot link a harvest from another campaign of the same farm', async () => {
  const season=await asUser(USER_A,()=>db.query(`INSERT INTO public.campaigns(workspace_id,farm_id,name,start_date) VALUES('${workspaceAId}','${farmId}','Other season','2027-10-01') RETURNING id;`));
  const harvest=await asUser(USER_A,()=>db.query(`INSERT INTO public.harvests(workspace_id,farm_id,campaign_id,harvest_date,weight_grams,recorded_by_user_id)
    VALUES('${workspaceAId}','${farmId}','${season.rows[0].id}',CURRENT_DATE,1000,'${USER_A}') RETURNING id;`));
  await assert.rejects(asUser(USER_A,()=>db.query(`UPDATE public.deliveries SET harvest_id='${harvest.rows[0].id}' WHERE id='${deliveryId}'`)),/harvest.*campaign/i);
});

for (const principal of ['creator','owner']) {
  test(`Audit: new farm cannot assign another account as ${principal} without consent`, async () => {
    const ws=await asUser(USER_D,()=>db.query(`INSERT INTO public.workspaces(owner_user_id,name) VALUES('${USER_D}','Principal isolation fixture') RETURNING id`));
    const creator=principal==='creator'?USER_A:USER_D;
    const owner=principal==='owner'?USER_A:USER_D;
    await assert.rejects(asUser(USER_D,()=>db.query(`INSERT INTO public.farms(workspace_id,creator_user_id,owner_id,name)
      VALUES('${ws.rows[0].id}','${creator}','${owner}','Unconsented principal')`)),/row-level security/i);
  });
}

test("Migration: unknown legacy permissive farm policy fails closed instead of remaining OR-authorized", async () => {
  const isolated = new PGlite();
  const root = fileURLToPath(new URL('../../', import.meta.url));
  try {
    await isolated.exec(readFileSync(join(root, 'supabase/tests/fixtures/mock_supabase_environment.sql'), 'utf8'));
    await isolated.exec(`CREATE TABLE public.farms (id uuid PRIMARY KEY, owner_id uuid, name text);
      ALTER TABLE public.farms ENABLE ROW LEVEL SECURITY;
      CREATE POLICY unexpected_public_access ON public.farms FOR SELECT USING (true);`);
    const migration = readFileSync(join(root, 'supabase/migrations/20261011120000_g0_auth_workspaces_shared_farms.sql'), 'utf8');
    await assert.rejects(isolated.exec(`BEGIN; ${migration} COMMIT;`), /legacy.*polic/i);
  } finally {
    await isolated.exec('ROLLBACK;');
    await isolated.close();
  }
});
