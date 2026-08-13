from datetime import date
from playwright.sync_api import expect
from registry import register_suite_setup
from registry import register_test


def extract_api_token(page, base_url: str = "http://localhost:8080") -> str:
    """Navigates to api-tokens.html and extracts the token string."""
    page.goto(f"{base_url}/settings/api-tokens")
    token_element = page.get_by_role("cell").locator('nth=1')
    token = token_element.inner_text()

    print(f"--> [Playwright] Extracted API Token: {token[:8]}...")
    return token.strip()


@register_suite_setup
def global_suite_setup(env):
    print("\n==========================================")
    print(" 🚀 RUNNING CLEAN GLOBAL SUITE SETUP")
    print("==========================================")
    env.reset_docker_volume()
    env.start_spring_boot()


@register_test(
    name="initial_setup",
    description="Clears PostGIS volume, runs Flyway/Liquibase migrations, configures admin password, and logs in."
)
def run_initial_setup_test(page, cp, env):
    today = date.today().isoformat()

    with cp.step(
            name="1. Admin Creation",
            target_url="http://localhost:8080",
            checklist=[
                "Verify browser was redirected to /setup",
                "Verify admin password setup succeeded",
                "Verify redirect to /login"
            ],
            verifier=lambda p: expect(p).to_have_url("http://localhost:8080/login")
    ):
        page.wait_for_url("**/setup")
        page.fill("input[name='password']", "AdminPass123!")
        page.click("button[type='submit']")
        page.wait_for_url("**/login")

    # ------------------------------------------------------------------
    # Step 2: Login with New Credentials
    # ------------------------------------------------------------------
    with cp.step(
            name="2. Admin Login",
            target_url="http://localhost:8080/login",
            checklist=["Verify admin login is successful"],
            verifier=lambda p: expect(p).to_have_url(f"http://localhost:8080/?startDate={today}&endDate={today}")
    ):
        page.fill("input[name='username']", "admin")
        page.fill("input[name='password']", "AdminPass123!")
        page.click("button[type='submit']")

    # ------------------------------------------------------------------
    # Step 3: Create a new user, verify token and device
    # ------------------------------------------------------------------

    userName = "testuser"
    userPassword = "testuser"

    with cp.step(
        name="3. Create a new user",
        target_url="http://localhost:8080/login",
        verifier=lambda p: expect(p.get_by_role("cell", name="Default", exact=True)).to_be_visible()
    ):
        page.fill("input[name='username']", "admin")
        page.fill("input[name='password']", "AdminPass123!")
        page.click("button[type='submit']")
        page.goto("http://localhost:8080/settings/user-management")
        page.get_by_role("button", name="Add New User").click()
        page.fill("input[name='username']", userName)
        page.get_by_role("textbox", name="Display Name").fill(userName)
        page.get_by_role("textbox", name="Password").fill(userPassword)
        page.get_by_role("button", name="Create").click()
        expect(page.get_by_text("User created successfully")).to_be_visible()
        page.get_by_title("Logout").click()

        page.fill("input[name='username']", userName)
        page.fill("input[name='password']", userPassword)
        page.click("button[type='submit']")

        page.get_by_title("Open settings…").click()

        page.get_by_role("link", name="Devices").click()
        expect(page.get_by_text("Your default device")).to_be_visible()
        page.get_by_role("link", name="API Tokens").click()
        expect(page.get_by_role("cell", name="Default", exact=True)).to_be_visible()


@register_test(
    name="gpx_data_ingestion",
    description="Streams GPX track points in background and verifies map update."
)
def run_gpx_ingestion_test(page, cp, env):
    gpx_process = None

    try:
        # Step 1: Login & Get API Token
        with cp.step(
            name="1. Extract API Token",
            target_url="http://localhost:8080/login",
            checklist=["Verify admin login is successful"],
            verifier=lambda p: token is not None
        ):
            page.fill("input[name='username']", "admin")
            page.fill("input[name='password']", "AdminPass123!")
            page.click("button[type='submit']")
            page.wait_for_url("http://localhost:8080/**")
            token = extract_api_token(page)

        # Step 2: Start Background Streaming & Verify UI Updates
        with cp.step(
            name="2. Stream GPX Points & Inspect Live Map",
            checklist=["Verify map updates with GPX points",
                       "Verify map is centered on GPX track"],
            target_url="http://localhost:8080/"
        ):
            # Start background GPX sender (interval=1 sec)
            gpx_process = env.start_gpx_sender(
                gpx_file_path="src/test/resources/data/gpx/20250617.gpx",
                token=token,
                interval=0.5
            )

            print("--> GPX points are now streaming in the background!")
            print("--> Proceeding with live UI assertions while sender runs...")


    finally:
        # Guarantee the background Java process is killed when test finishes or fails
        env.stop_gpx_sender(gpx_process)
