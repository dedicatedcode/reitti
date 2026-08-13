from typing import Callable, Dict, Any

TEST_REGISTRY: Dict[str, Dict[str, Any]] = {}
SUITE_SETUPS: Dict[str, Callable] = {}


def register_test(name: str, description: str = ""):
    """Decorator to register an individual test scenario."""

    def decorator(func: Callable):
        TEST_REGISTRY[name] = {
            "func": func,
            "description": description,
            "module": func.__module__,  # Automatically tracks which module this test belongs to
        }
        return func

    return decorator


def register_suite_setup(func: Callable):
    """Decorator to register a setup function scoped to the scenario file where it is defined."""
    module_name = func.__module__
    SUITE_SETUPS[module_name] = func
    return func