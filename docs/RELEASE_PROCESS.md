## 🚦 Release Process

This project uses a **controlled-release** workflow. Releases are triggered manually from the GitHub Actions UI by selecting a source branch and entering a version number. The workflow creates a draft release, opens a verification issue, and pauses for manual approval before building Docker images and publishing assets.

## How to Start a Release

1. Navigate to **Actions** in your repository.
2. In the left sidebar, click the **controlled-release** workflow.
3. Click **Run workflow** (drop‑down button on the right).
4. Choose the **source branch** (usually `main`).
5. Enter the **version** (e.g., `3.5.1` – without the leading `v`).
6. Click **Run workflow**.

The workflow will:
- Create a Git tag matching the version you supplied (e.g., `v3.5.1`).
- Build the project’s JAR.
- Create a draft GitHub Release with auto‑generated release notes.
- Create a **verification issue** whose title matches the version number.
- Pause at the `release-gate` environment, waiting for manual approval.

## How to Approve the Release

Once the workflow pauses, you must manually verify the build and approve the deployment to continue.

### 1. Complete Manual Verification
- Run the application locally or on a test instance using Docker Compose
- Walk through the verification checklist (auth flows, map/tiles, live mode, import pipeline, multi‑user isolation, i18n/units, etc.)
- Update the auto‑created verification issue with your findings or confirm all checklist items are completed
- Do not approve until all critical paths are verified

### 2. Approve via GitHub UI (Recommended)
1. Navigate to **Actions** in your repository
2. Click the pending workflow run named `controlled-release / Build Release`
3. In the workflow summary, locate the job that shows `Waiting`
4. Click **Review deployments** in the top‑right corner
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

## What Happens After Approval
- The workflow resumes, pushes multi‑arch Docker images to Docker Hub and GHCR, and publishes the GitHub Release.
- The verification issue is automatically closed with a success comment linking to the workflow run.
- Check the **Actions** tab to confirm all steps completed successfully.
- You can now share the release notes and notify users.

## Environment Management
- Add or remove approvers: `Settings → Environments → release-gate → Required reviewers`
- Enable `Prevent self‑approval` in the same menu if you require a second maintainer to verify
- Approvals are tied to the environment, not individual runs, so permissions persist across releases

## What happens if I don't approve?
- The workflow stays in a `Waiting` state.
- **The tag is NOT deleted automatically.** Git tags are permanent references.
- If you decide to abort the release, you must cancel the workflow run and delete the tag manually:

```bash
# Cancel the pending workflow run via UI or CLI
gh run cancel <run-id>

# Delete the tag locally & remotely
git tag -d v3.5.1
git push origin --delete v3.5.1
```
