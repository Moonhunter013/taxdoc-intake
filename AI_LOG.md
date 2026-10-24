# AI Usage Log

How AI tools were used on this project, and where human judgment overrode them.

| Date | Tool | Task | What I kept | What I changed / rejected, and why |
|---|---|---|---|---|
| 2026-10-23 | Claude (Cowork) | Week 0 scaffold: repo layout, service skeletons, compose, CI, synthetic form generator | Monorepo layout, FastAPI/Spring Boot/Angular skeletons, docker-compose, CI workflow, ADR template |...|
| 2026-10-23 | Claude (Cowork) | Spring Boot version | Upgrade steps (starter rename, `spring-boot-starter-webmvc-test`) | Scaffold targeted Boot 3.5.6. Checked spring.io (current GA 4.1.2) and upgraded. First build failed: `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure` in Boot 4. Fixed and verified with `mvnw verify`. See ADR-0002. |
| 2026-10-23 | Claude (Cowork) | Synthetic tax data | Client scenarios, form generator, ground-truth JSON | Data was originally written for tax year 2025, but the IRS forms I downloaded are 2026 revisions. Switched to 2026 and updated the Social Security wage base to $184,500 (SSA announcement). _TODO: recompute one W-2 by hand against `samples/ground-truth/` and note the result here._ |
| 2026-10-23 | Claude (Cowork) | Frontend tooling | Angular 21 skeleton | npm 10 crashed resolving Angular 21 peer dependencies (`Cannot read properties of null (reading 'edgesOut')`). Pinned npm 11 via `packageManager` and in CI. |
| 2026-10-23 | AWS console | Admin access | - | Console offered to create a root access key. Declined: root keys have unrestricted access. Created an admin IAM user with MFA for admin work instead. |
| 2026-10-24 | Claude (Cowork) | IAM permissions | Least-privilege policy (`dev-user-policy.json`) | Textract smoke test failed with AccessDenied for `taxdoc-dev`. Diagnosed with `aws iam list-groups-for-user`: the group had never been created. Fixed from the CLI with a `taxdoc-developers` group and a customer-managed policy instead of an inline one. Smoke test then passed (392 Textract blocks). |
| 2026-10-24 | Claude (Cowork) | Windows setup docs | AWS setup steps | AI-written docs used bash syntax. Adapted them to PowerShell and rewrote `infra/aws/README.md`. Deferred saving Textract output to files: PowerShell redirection can corrupt non-ASCII text, so fixtures will be captured with Python. |

## Notable catches

**Outdated framework version.** The scaffold pinned Spring Boot 3.5.6, which was behind the current
4.1.2 release. Upgrading at two files was cheap; doing it later would not be. The failed build after
the bump (moved test annotation package) is a good example of why I verify agent changes with a real
build rather than trusting that they compile. See `docs/adr/0002-spring-boot-4.md`.

**Permissions failure that looked like a config issue.** The error said "no identity-based policy
allows s3:PutObject". Rather than loosening the policy, I checked group membership first and found
the group was missing. Least privilege stayed intact.
