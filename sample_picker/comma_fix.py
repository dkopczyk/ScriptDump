# Define the input and output file paths
input_file = "oneLiners.txt"
output_file = "output_fazzini.txt"

# Open the input and output files
with open(input_file, "r") as infile, open(output_file, "w") as outfile:
    # Iterate through each line in the input file
    for line in infile:
        # Split the line by commas
        parts = line.strip().split(",")
        
        # Check if the line has at least three commas
        if len(parts) >= 4:
            # Replace the first, second, and third comma with <FAZZINI>
            modified_line = "<FAZZINI>".join(parts[:4]) + "," + ",".join(parts[4:])
        else:
            # If the line doesn't have enough commas, leave it unchanged
            modified_line = line
        
        # Write the modified line to the output file
        outfile.write(modified_line + "\n")

print("File processing complete. Output saved to", output_file)
