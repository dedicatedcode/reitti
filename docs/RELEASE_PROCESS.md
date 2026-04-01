## 🚦 Release Gate & Tag Cleanup

This project uses a single-branch flow (`main` → tag → publish). Pushing a version tag triggers a CI workflow that **pauses for manual verification** before building Docker images or publishing assets.

## How to Approve the Release

Once you push a version tag (e.g., `v4.2.0`), the release workflow automatically pauses at the `release-gate` environment and waits for manual verification. Follow these steps to approve and continue the build:

### 1. Complete Manual Verification
- Run the application locally or on a test instance using Docker Compose
- Walk through the verification checklist (auth flows, map/tiles, live mode, import pipeline, multi-user isolation, i18n/units, etc.)
- Update the auto-created GitHub issue titled with the version number with your findings or confirm all checklist items are completed
- Do not approve until all critical paths are verified

### 2. Approve via GitHub UI (Recommended)
1. Navigate to **Actions** in your repository
2. Click the pending workflow run named `release / Build Release` triggered by your tag
3. In the workflow summary, locate the job that shows `Waiting`
4. Click **Review deployments** in the top-right corner
5. Select **Approve and deploy**
6. Optionally add a comment (e.g., `Checklist verified, all systems nominal.`) and confirm
7. The workflow will immediately resume from where it paused

### 3. Approve via GitHub CLI (Alternative)
If you prefer the terminal, you can approve the pending run directly:
```bash
# Find the latest waiting run for this repo
RUN_ID=$(gh run list --status waiting --limit 1 --json databaseId -q '.[0].databaseId')

# Approve it
gh run approve "$RUN_ID"
```
Note: Your GitHub account must be listed as a required reviewer for the `release-gate` environment for CLI approval to succeed.

### What Happens After Approval
- The workflow resumes, builds the JAR, pushes multi-arch Docker images to Docker Hub and GHCR, and publishes the GitHub Release
- The verification issue is automatically closed with a success comment linking to the workflow run
- Check the **Actions** tab to confirm all steps completed successfully
- You can now share the release notes and notify users

### Environment Management
- Add or remove approvers: `Settings -> Environments -> release-gate -> Required reviewers`
- Enable `Prevent self-approval` in the same menu if you require a second maintainer to verify
- Approvals are tied to the environment, not individual runs, so permissions persist across releases

### What happens if I don't approve?
- The workflow stays in a `Waiting` state.
- **The tag is NOT deleted automatically.** Git tags are permanent references.
- If you decide to abort the release, you must cancel the workflow run and delete the tag manually:

```bash
# Cancel the pending workflow run via UI or CLI
gh run cancel <run-id>

# Delete the tag locally & remotely
git tag -d v4.2.0
git push origin --delete v4.2.0