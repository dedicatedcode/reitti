from playwright.sync_api import Page, APIRequestContext
import subprocess

class Actions:
    """Library of reusable UI, API, and DB pre-actions."""

    # --- UI ACTIONS ---
    @staticmethod
    def ui_login(page: Page, email: str = "admin@example.com", password: str = "secret"):
        def _action():
            print(f"--> [UI] Logging in as {email}...")
            page.goto("http://localhost:3000/login")
            page.fill("#email", email)
            page.fill("#password", password)
            page.click("button[type=submit]")
            page.wait_for_url("**/dashboard")
        return _action

    @staticmethod
    def ui_create_user(page: Page, username: str, role: str):
        def _action():
            print(f"--> [UI] Creating user: {username} ({role})...")
            page.goto("http://localhost:3000/admin/users/new")
            page.fill("input[name='username']", username)
            page.select_option("select[name='role']", role)
            page.click("button:has-text('Save User')")
            page.wait_for_selector(".success-message")
        return _action

    # --- API ACTIONS (Fast Data Seeding) ---
    @staticmethod
    def api_create_user(api: APIRequestContext, username: str, role: str):
        """Creates user via API directly — 10x faster than doing it through the UI!"""
        def _action():
            print(f"--> [API] Fast-creating user: {username}")
            res = api.post("http://localhost:3000/api/v1/users", data={
                "username": username,
                "role": role
            })
            assert res.ok, f"Failed to create user via API: {res.status_text}"
        return _action

    # --- DB / DOCKER ACTIONS ---
    @staticmethod
    def reset_db(sql_dump_file: str):
        def _action():
            print(f"--> [DB] Restoring clean state from {sql_dump_file}...")
            subprocess.run([
                "docker", "exec", "-i", "my_postgres_db",
                "psql", "-U", "postgres", "-d", "myapp"
            ], stdin=open(sql_dump_file, "r"), check=True)
        return _action