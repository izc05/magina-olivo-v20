# ADR-008 — Android Phase 1 Toolchain

Status: Accepted for RC1 Phase 1  
Date: 2026-09-17

## Decision

Mágina Olivo Phase 1 uses JDK 17, Gradle 9.4.1, AGP 9.2.1, AGP built-in Kotlin with KGP explicitly upgraded to Kotlin 2.4.20, Compose Compiler 2.4.20, compileSdk/targetSdk 37 and Compose BOM 2026.08.00.

## Why

The combination stays inside the documented Gradle/Kotlin/AGP compatibility ranges used by the Phase 1 plan, supports API 37 and avoids dynamic dependency upgrades. Built-in Kotlin is retained because AGP 9 enables it by default and Android recommends migration to it rather than opting out.

## Consequence

Changing AGP/Kotlin/Gradle as part of an unrelated feature is prohibited. A toolchain update requires its own reviewed dependency/ADR change.
