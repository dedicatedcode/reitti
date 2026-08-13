from playwright.sync_api import expect
from registry import register_test
from registry import register_suite_setup
from datetime import date


@register_suite_setup
def global_suite_setup(env):
    print("\n==========================================")
    print(" 🚀 RUNNING ON MIGRATION SUITE")
    print("==========================================")
    env.reset_docker_volume()
    env.import_pg_dump("backup_20260812_125122.dump")
    env.start_spring_boot()

@register_test(
    name="after_migration",
    description="Should have a user and device created after migration."
)
def run_initial_setup_test(page, cp, env):
    today = date.today().isoformat()

    # ------------------------------------------------------------------
    # Step 1: Login with New Credentials
    # ------------------------------------------------------------------
    with cp.step(
            name="1. Admin Login",
            target_url="http://localhost:8080/login",
            checklist=["Verify admin login is successful"],
            verifier=lambda p: expect(p).to_have_url(f"http://localhost:8080/?startDate={today}&endDate={today}")
    ):
        page.fill("input[name='username']", "admin")
        page.fill("input[name='password']", "admin")
        page.click("button[type='submit']")
