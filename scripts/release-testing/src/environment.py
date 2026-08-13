from pathlib import Path
import subprocess
import time
import urllib.request
from typing import Optional

class EnvironmentManager:
    def __init__(
            self,
            compose_file: str = "docker-compose-dev.yml",
            project_root: str | Path = Path("../..")
    ):
        self.compose_file = compose_file
        self.project_root = Path(project_root).resolve()
        self.spring_process: Optional[subprocess.Popen] = None

    def reset_docker_volume(self):
        print("\n--> [Docker] Gracefully stopping containers...")

        # 1. Stop containers gracefully first (gives RabbitMQ time to close Mnesia DB cleanly)
        subprocess.run(
            ["docker", "compose", "--ansi", "never", "-f", self.compose_file, "stop", "-t", "10"],
            cwd=self.project_root,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False
        )

        # 2. Down containers, remove volumes and orphans
        print("--> [Docker] Removing containers and wiping volumes...")
        subprocess.run(
            ["docker", "compose", "--ansi", "never", "-f", self.compose_file, "down", "-v", "--remove-orphans"],
            cwd=self.project_root,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False
        )

        # 3. Brief delay to ensure network ports & locks are released by OS
        time.sleep(2)

        print("--> [Docker] Spinning up clean containers...")
        res = subprocess.run(
            ["docker", "compose", "--ansi", "never", "-f", self.compose_file, "up", "-d"],
            cwd=self.project_root,
            capture_output=True,
            text=True
        )

        if res.returncode != 0:
            print(f"\n[!] Docker Compose Up Failed:\n{res.stderr}")
            raise RuntimeError(f"Docker compose failed to start services:\n{res.stderr}")

        print("--> [Docker] Clean environment ready.")

    def wait_for_postgis(self, timeout: int = 30):
        """Waits until the PostGIS container is healthy and ready to accept connections."""
        print("--> [PostGIS] Waiting for database readiness...")
        start_time = time.time()

        while time.time() - start_time < timeout:
            res = subprocess.run(
                [
                    "docker", "compose", "-f", self.compose_file, "exec", "-T", "postgis",
                    "pg_isready", "-U", "reitti", "-d", "reittidb"
                ],
                cwd=self.project_root,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            if res.returncode == 0:
                print("--> [PostGIS] Database is ready!")
                return True
            time.sleep(1)

        raise TimeoutError("PostGIS container did not become ready within the timeout period.")

    def import_pg_dump(self, dump_file_path: str):
        """Imports a .sql or .dump/.tar pgdump file and prints all diagnostic output."""
        dump_path = Path(dump_file_path)
        if not dump_path.is_absolute():
            dump_path = self.project_root / dump_path

        if not dump_path.exists():
            raise FileNotFoundError(f"Dump file not found at: {dump_path.resolve()}")

        # 1. Ensure PostGIS is ready
        self.wait_for_postgis()

        print(f"\n--> [PostGIS] Starting import for: {dump_path.name}")
        print(f"--> [PostGIS] Path: {dump_path.resolve()}")

        # 2. Build import command
        if dump_path.suffix in [".dump", ".tar", ".dir"]:
            cmd = [
                "docker", "compose", "-f", self.compose_file, "exec", "-T", "postgis",
                "pg_restore",
                "-U", "reitti",
                "-d", "reittidb",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-privileges"
            ]
        else:
            cmd = [
                "docker", "compose", "-f", self.compose_file, "exec", "-T", "postgis",
                "psql",
                "-U", "reitti",
                "-d", "reittidb",
                "-v", "ON_ERROR_STOP=1"  # Force psql to fail immediately on bad SQL statements
            ]

        # 3. Stream dump file and capture output
        with open(dump_path, "rb") as dump_file:
            res = subprocess.run(
                cmd,
                cwd=self.project_root,
                stdin=dump_file,
                capture_output=True,
                text=True
            )

        # 4. ALWAYS print output so you can inspect what happened
        print("\n" + "─" * 60)
        print(" 📄 POSTGIS IMPORT STDOUT:")
        print("─" * 60)
        print(res.stdout.strip() if res.stdout.strip() else "(empty)")

        print("\n" + "─" * 60)
        print(" ⚠️  POSTGIS IMPORT STDERR:")
        print("─" * 60)
        print(res.stderr.strip() if res.stderr.strip() else "(empty)")
        print("─" * 60 + "\n")

        # 5. DB Verification: Query count of public tables
        verify_cmd = [
            "docker", "compose", "-f", self.compose_file, "exec", "-T", "postgis",
            "psql", "-U", "reitti", "-d", "reittidb", "-t", "-c",
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"
        ]
        count_res = subprocess.run(verify_cmd, cwd=self.project_root, capture_output=True, text=True)
        table_count = count_res.stdout.strip() if count_res.returncode == 0 else "unknown"

        print(f"--> [PostGIS Verification] Total public tables in database: {table_count}\n")

        if res.returncode != 0:
            raise RuntimeError(f"pgdump import failed with exit code {res.returncode}")

    def start_spring_boot(self, profile: str = "dev", url: str = "http://localhost:8080", timeout: int = 120):
        """Starts Spring Boot with Maven inside project_root where pom.xml resides."""
        print(f"\n--> [Spring Boot] Launching Maven inside: {self.project_root}...")

        self.spring_process = subprocess.Popen(
            ["mvn", "spring-boot:run", f"-Dspring-boot.run.profiles={profile}"],
            cwd=self.project_root,  # <--- FIX: Directs Maven to run in the main app directory
            stdout=subprocess.DEVNULL,
            stderr=subprocess.STDOUT
        )

        print(f"--> [Spring Boot] Waiting for {url} to respond...")
        start_time = time.time()
        while time.time() - start_time < timeout:
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "Playwright-Runner"})
                with urllib.request.urlopen(req, timeout=2) as res:
                    if res.status in (200, 302):
                        print("--> [Spring Boot] Application is UP!")
                        return
            except Exception:
                time.sleep(3)

            if self.spring_process.poll() is not None:
                raise RuntimeError("Spring Boot crashed during startup! Check Maven logs.")

        raise TimeoutError(f"Spring Boot did not respond at {url} within {timeout}s.")

    def stop_spring_boot(self):
        if self.spring_process and self.spring_process.poll() is None:
            print("\n--> [Spring Boot] Stopping Maven process...")
            self.spring_process.terminate()
            self.spring_process.wait()

    def build_gpx_sender(self) -> Path:
        """Ensures the GPX sender JAR is compiled via Maven."""
        tool_dir = self.project_root / "docs" / "tools" / "gpx-tools"
        jar_path = tool_dir / "target" / "gpx-sender-1.0.0.jar"

        if not jar_path.exists():
            print("\n--> [GPX Sender] JAR not found. Compiling with Maven...")
            res = subprocess.run(
                ["mvn", "clean", "package"],
                cwd=tool_dir,
                capture_output=True,
                text=True
            )
            if res.returncode != 0:
                print(f"[!] Maven Build Failed:\n{res.stderr}")
                raise RuntimeError("Failed to build gpx-sender JAR")
            print("--> [GPX Sender] Build successful!")

        return jar_path

    def start_gpx_sender(
            self,
            gpx_file_path: str,
            token: str,
            target_url: str = "http://localhost:8080",
            interval: int = 1
    ) -> subprocess.Popen:
        """Starts sending GPX data in a non-blocking background process."""
        jar_path = self.build_gpx_sender()

        gpx_path = Path(gpx_file_path)
        if not gpx_path.is_absolute():
            gpx_path = self.project_root / gpx_path

        if not gpx_path.exists():
            raise FileNotFoundError(f"GPX file not found at: {gpx_path.resolve()}")

        cmd = [
            "java", "-jar", str(jar_path.resolve()),
            str(gpx_path.resolve()),
            "--url", target_url,
            "--token", token,
            "--interval", str(interval)
        ]

        print(f"\n--> [GPX Sender] Starting background streaming...")
        print(f"    File: {gpx_path.name}")
        print(f"    Target: {target_url}")
        print(f"    Interval: {interval}s")

        # Popen runs the Java command in the background without blocking Python
        process = subprocess.Popen(
            cmd,
            cwd=self.project_root,
            stdout=subprocess.DEVNULL,  # Prevents output from cluttering console
            stderr=subprocess.PIPE     # Retain stderr in case of errors
        )
        return process

    def stop_gpx_sender(self, process: Optional[subprocess.Popen]):
        """Safely terminates a running GPX sender background process."""
        if process and process.poll() is None:  # Process is still running
            print("--> [GPX Sender] Stopping background process...")
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
            print("--> [GPX Sender] Stopped.")