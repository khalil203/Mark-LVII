"""Self-update: pull the latest Mark-LVII from upstream and refresh deps."""
import subprocess, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
UPSTREAM = "https://github.com/FatihMakes/Mark-LVII.git"

def run(args):
    print(">", " ".join(args))
    subprocess.run(args, check=True, cwd=HERE)

def main():
    # Ensure upstream remote exists
    remotes = subprocess.check_output(["git", "remote"], cwd=HERE, text=True)
    if "upstream" not in remotes.split():
        run(["git", "remote", "add", "upstream", UPSTREAM])
    run(["git", "fetch", "upstream"])
    run(["git", "merge", "upstream/main", "--no-edit"])
    # Refresh dependencies
    run([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"])
    print("\n✅ Self-update complete. Restart the app.")

if __name__ == "__main__":
    main()
