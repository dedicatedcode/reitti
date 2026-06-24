## Manual Verification Checklist
Run the application locally or on a test instance against commit {{ COMMIT_SHA }}.

- [ ] Upgrade: Follow the upgrade instructions in the release notes
- [ ] Upgrade: Did the migration step run successfully?
- [ ] Auth: Local login + session persistence
- [ ] Auth: OIDC login, profile sync, logout callback
- [ ] Map: Tiles load, fullscreen toggle, colored map
- [ ] Live mode: Incoming points refresh the map
- [ ] Timeline: Trips/visits display, duration/distance accurate
- [ ] Timeline: Visit and Trip selection focuses the map on the selected item
- [ ] Timeline: Trip allows editing the detected transportation mode
- [ ] Timeline: Visit allows editing the place
- [ ] Place-edit: Shows nearby places as points on the map
- [ ] Place-edit: allows editing/removing/adding a polygon
- [ ] Multi-user: Data isolation confirmed (no cross-leak)
- [ ] Multi-user: Tracks and Trips visible to all connected users
- [ ] Multi-user: Live View visible to all connected users
- [ ] Workbench: Are multiple devices displayed and selectable?
- [ ] Workbench: Are points selected and highlighted? Can we move or remove them?
- [ ] Workbench: Does the map follow the selected points?
- [ ] Workbench: Does stitching a device timeline into the main timeline work?
- [ ] Workbench: Revert actions do not break the workbench?
- [ ] Workbench: Saving works and the main timeline gets adjusted?
- [ ] Visit Sensitivity: Create a new setting, verify that the recalculation works
- [ ] Import: GPX/GeoJSON triggers queue, status visible in UI
- [ ] Geocoding: Failover handled gracefully (no UI freeze)
- [ ] i18n/Units: Language & Metric/Imperial switches work

## Next Steps
1. Verify against this exact tag: `{{ TAG }}`
2. Check all boxes above
3. Return to this workflow run: [{{ RUN_URL }}]({{ RUN_URL }})
4. Click **Review deployments** → **Approve and deploy**
5. This issue will auto-close once the release succeeds
