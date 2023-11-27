import os

# Path to the text file with list of repos from google doc
file_path = ""

# Local repository directory
local_repo_directory = ""

# Read the file
with open(file_path, "r") as file:
    lines = file.readlines()

for line in lines:
    # Split the entry using the <FAZZINI> delimiter
    parts = line.split("<FAZZINI>")

    # Extract relevant information
    repo_url = parts[0].strip()
    repo_name = repo_url.split("/")[-1].split(".")[0].strip()
    class_name = parts[1].strip()

    # Construct the local repository path
    local_repo_path = os.path.join(local_repo_directory, repo_name)

    # Check if the local repository already exists
    if not os.path.exists(local_repo_path):
        user_input = input(f"Repository '{repo_name}' doesn't exist locally. Do you want to clone it? (yes/no): ").lower()

        if user_input == "yes":
            os.system(f"git clone {repo_url} {local_repo_path}")
            print(f"Repository '{repo_name}' cloned successfully.\n")
        else:
            print(f"Skipping cloning of repository '{repo_name}'.\n")
    else:
        print(f"Repository '{repo_name}' already exists locally. Skipping cloning.\n")