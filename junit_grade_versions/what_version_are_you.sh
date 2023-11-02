#!/bin/bash
ACCESS_TOKEN="<sneaky>"

# List of GitHub Repos
# repos=(
#     "https://github.com/wordpress-mobile/WordPress-Android.git"
#     "https://github.com/commons-app/apps-android-commons.git"
#     "https://github.com/fossasia/open-event-organizer-android.git"
#     "https://github.com/steve-goldman/volksempfaenger.git"
#     "https://github.com/openMF/mifos-mobile.git"
# )

csv_file="$1"
if [ ! -f "$csv_file" ]; then
  echo "Error: CSV file '$csv_file' doesnt exist."
  exit 1
fi

while IFS= read -r repo; do
    repos+=("$repo")
done < "$csv_file"

# array to store results
results=()

GITHUB_API_URL="https://api.github.com/repos"

# curl https://api.github.com/repos/airbnb/javascript/contents/package.json | jq -r ".content" | base64 --decode
# Loop through the list of repositories
for repo in "${repos[@]}"; do

    # Remove "https://github.com/" from the beginning and ".git" from the end of each string
    repo_name="${repo#https://github.com/}"
    repo_name="${repo_name%.git}"
    # echo $repo_name 
    # fetch the build.gradle file contents from root 
    response=$(curl -s -H "Authorization: token $ACCESS_TOKEN" "$GITHUB_API_URL/$repo_name/contents/build.gradle")
    # echo "$response"

    if [ -n "$response" ]; then
        build_gradle_content=$(echo "$response" | jq -r '.content' | base64 --decode)
        # echo "$build_gradle_content"

        if [ -n "$build_gradle_content" ]; then
            # echo "build.gradle Content in $repo_name:"
            # echo "$build_gradle_content"

            # Use grep to find the junitVersion in the build.gradle file
            junit_version=$(echo "$build_gradle_content" | grep -oiE '^\s*jUnitVersion\s*=\s*["'\'']?[0-9.]+["'\'']?' | grep -oE '[0-9.]+')

            if [ -n "$junit_version" ]; then
                results+=("$repo, $junit_version")
            else
                # Try in the app level gradle file
                response=$(curl -s -H "Authorization: token $ACCESS_TOKEN" "$GITHUB_API_URL/$repo_name/contents/app/build.gradle")
                build_gradle_content=$(echo "$response" | jq -r '.content' | base64 --decode)
                junit_line=$(echo "$build_gradle_content" | grep -E "junit:junit:[0-9.]+")

                option2=$(echo "$build_gradle_content" | grep -o "junit:junit:[^']*")
                if [ -n "$option2" ]; then
                    echo "$repo, $option2"
                    results+=("$repo, $option2")
                else 
                    echo "$repo, JUnit Version not found"
                    results+=("$repo, NOT FOUND")
                fi
            fi
        else
            results+=("$repo, NOT FOUND")
            # echo "$repo, Failed to decode content"
        fi
    else
        results+=("$repo, NOT FOUND")
        # echo "$repo, build.gradle not found"
    fi
done

for result in "${results[@]}"; do
    echo "$result"
done