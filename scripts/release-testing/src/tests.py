import argparse
import importlib
import pkgutil
import sys
from playwright.sync_api import sync_playwright

import registry
from checkpoint import CheckpointManager
from environment import EnvironmentManager
import scenarios


def load_all_scenarios():
    for _, module_name, _ in pkgutil.iter_modules(scenarios.__path__):
        importlib.import_module(f"scenarios.{module_name}")


def main():
    load_all_scenarios()

    parser = argparse.ArgumentParser(
        description="Reitti E2E Test Suite Runner"
    )
    parser.add_argument("-t", "--test", help="Name of a specific test to run")
    parser.add_argument(
        "-l",
        "--list",
        action="store_true",
        help="List all available tests",
    )
    parser.add_argument(
        "--skip-setup",
        action="store_true",
        help="Skip suite setup functions",
    )
    args = parser.parse_args()

    if args.list:
        print("\nRegistered Tests:")
        for name, info in registry.TEST_REGISTRY.items():
            print(
                f"  • {name:<20} - {info['description']} [{info['module']}]"
            )
        return

    if args.test:
        if args.test not in registry.TEST_REGISTRY:
            print(f"Error: Test '{args.test}' not found.")
            sys.exit(1)
        selected_tests = {args.test: registry.TEST_REGISTRY[args.test]}
    else:
        selected_tests = registry.TEST_REGISTRY

    # Group selected tests by their module name
    tests_by_module = {}
    for test_name, test_info in selected_tests.items():
        mod = test_info["module"]
        if mod not in tests_by_module:
            tests_by_module[mod] = []
        tests_by_module[mod].append((test_name, test_info))

    env = EnvironmentManager(
        compose_file="docker-compose-dev.yml", project_root="../../"
    )

    try:
        with sync_playwright() as p:
            browser = p.firefox.launch(
                headless=False, args=["--start-maximized"]
            )
            context = browser.new_context(
                viewport={"width": 1920, "height": 1080}
            )
            page = context.new_page()
            cp = CheckpointManager(context, page)

            # Iterate over modules and run their specific setups before their tests
            for module_name, tests in tests_by_module.items():

                # 1. Run setup ONLY for this specific scenario module
                if module_name in registry.SUITE_SETUPS and not args.skip_setup:
                    print(f"\n==========================================")
                    print(f" 🚀 RUNNING SETUP FOR: {module_name}")
                    print(f"==========================================")
                    registry.SUITE_SETUPS[module_name](env)

                # 2. Run all tests belonging to this module
                for test_name, test_info in tests:
                    print(f"\n==========================================")
                    print(f" EXECUTING SCENARIO: {test_name}")
                    print(f" Description: {test_info['description']}")
                    print(f"==========================================")
                    test_info["func"](page, cp, env)

            browser.close()

    finally:
        env.stop_spring_boot()


if __name__ == "__main__":
    main()