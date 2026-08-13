from contextlib import contextmanager
from typing import Callable, List, Optional
from playwright.sync_api import Page, BrowserContext

class CheckpointManager:
    def __init__(self, context: BrowserContext, page: Page):
        self.context = context
        self.page = page

    def _ensure_page_alive(self) -> Page:
        if self.page.is_closed():
            print("--> Browser page was closed/disconnected. Creating a fresh page...")
            self.page = self.context.new_page()
        return self.page

    def _print_checklist_box(self, name: str, checklist: Optional[List[str]] = None, error: Optional[Exception] = None):
        """Prints a high-visibility box in the console when execution pauses."""
        width = 68
        print("\n" + "═" * width)
        if error:
            print(f" ❌ STEP ERROR: {name}".ljust(width - 1))
            print("─" * width)
            print(f" Error details: {error}")
        else:
            print(f" 🔍 MANUAL VERIFICATION REQUIRED: {name}".ljust(width - 1))

        print("═" * width)
        if checklist:
            for item in checklist:
                print(f"  [ ] {item}")
        else:
            print("  (No checklist items specified - inspect current browser state)")
        print("─" * width)
        print(" ⏸️  Execution PAUSED. Resume or step through in Playwright Inspector.")
        print("═" * width + "\n")

    def _safe_pause(self, page: Page):
        """Pauses execution safely if the page is still open."""
        if not page.is_closed():
            page.pause()

    @contextmanager
    def step(
            self,
            name: str,
            target_url: Optional[str] = None,
            pre_actions: Optional[List[Callable]] = None,
            checklist: Optional[List[str]] = None,
            verifier: Optional[Callable[[Page], None]] = None
    ):
        print(f"\n==========================================")
        print(f" RUNNING STEP: {name}")
        print(f"==========================================")

        if pre_actions:
            for action in pre_actions:
                action()

        page = self._ensure_page_alive()

        if target_url:
            page.goto(target_url)

        # 1. CATCH ERRORS OCCURRING INSIDE THE 'with' BLOCK
        try:
            yield page
        except Exception as step_error:
            print(f"\n[!] Error during execution of step '{name}'")
            self._print_checklist_box(name, checklist, error=step_error)
            self._safe_pause(page)
            raise step_error  # Re-raise error after pausing so runner handles failure

        # 2. RUN VERIFIER OR PAUSE FOR MANUAL INSPECTION IF STEP SUCCEEDED
        if verifier:
            print(f"--> Running auto-verification for [{name}]...")
            try:
                verifier(page)
                print(f"--> [AUTO-VERIFIED] Passed!")
            except Exception as verify_error:
                print(f"\n[!] AUTO-VERIFICATION FAILED for [{name}]")
                self._print_checklist_box(name, checklist, error=verify_error)
                self._safe_pause(page)
        else:
            self._print_checklist_box(name, checklist)
            self._safe_pause(page)