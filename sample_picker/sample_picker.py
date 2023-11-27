import random

sample_size = 335
confidence_level = 0.95
margin_of_error = 0.05

# Read the data from the file and store it in a list
with open("output_fazzini.txt", "r") as file:
    data = file.readlines()

strata = {}
for line in data:
    parts = line.strip().split('<FAZZINI>')
    repo_url = parts[0]
    if repo_url not in strata:
        strata[repo_url] = []
    strata[repo_url].append(line)

total_strata = len(strata)
total_sample_size = sample_size

sample = []

for repo_url, lines in strata.items():
    stratum_size = len(lines)
    stratum_sample_size = int((stratum_size / len(data)) * total_sample_size)

    if stratum_sample_size >= len(lines):
        sample.extend(lines)
        total_sample_size -= len(lines)
    else:
        sampled_lines = random.sample(lines, stratum_sample_size)
        sample.extend(sampled_lines)
        total_sample_size -= stratum_sample_size

# TODO, I don't think I need this
random.shuffle(sample)

# If the total sample size is still less than the desired size, add more random lines from the data
if total_sample_size > 0:
    remaining_lines = [line for line in data if line not in sample]
    additional_sample = random.sample(remaining_lines, total_sample_size)
    sample.extend(additional_sample)


with open("sampled_data.txt", "w") as sample_file:
    for line in sample:
        sample_file.write(line)

print(f"Sampled {len(sample)} lines.")
