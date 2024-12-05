import pandas as pd
import matplotlib.pyplot as plt

# Load the CSV file
csv_file = "speeds.output.csv"  # Replace with your CSV file name
data = pd.read_csv(csv_file)

# Rename columns
data.rename(columns={"Nodes": "Number of Nodes"}, inplace=True)
data.rename(columns={"Packets": "Number of Packets per Second"}, inplace=True)
data.rename(columns={"Speeds": "Speed of Nodes"}, inplace=True)

# Create the plots
plt.figure(figsize=(12, 10))

# Network_Throughput vs Speed of Nodes
plt.subplot(2, 2, 1)
plt.plot(data["Speed of Nodes"], data["Network_Throughput"], marker='o', label="Network Throughput")
plt.xlabel("Speed of Nodes")
plt.ylabel("Network Throughput")
plt.title("Network Throughput vs Speed of Nodes")
plt.grid(True)

# Avg_Packet_Delivery_Ratio vs Speed of Nodes
plt.subplot(2, 2, 2)
plt.plot(data["Speed of Nodes"], data["Avg_Packet_Delivery_Ratio"], marker='o', color='g', label="Packet Delivery Ratio")
plt.xlabel("Speed of Nodes")
plt.ylabel("Average Packet Delivery Ratio")
plt.title("Avg Packet Delivery Ratio vs Speed of Nodes")
plt.grid(True)

# Avg_Packet_Drop_Ratio vs Speed of Nodes
plt.subplot(2, 2, 3)
plt.plot(data["Speed of Nodes"], data["Avg_Packet_Drop_Ratio"], marker='o', color='r', label="Packet Drop Ratio")
plt.xlabel("Speed of Nodes")
plt.ylabel("Average Packet Drop Ratio")
plt.title("Avg Packet Drop Ratio vs Speed of Nodes")
plt.grid(True)

# Avg_EndToEndDelay vs Speed of Nodes
plt.subplot(2, 2, 4)
plt.plot(data["Speed of Nodes"], data["Avg_EndToEndDelay"], marker='o', color='m', label="End-to-End Delay")
plt.xlabel("Speed of Nodes")
plt.ylabel("Average End-to-End Delay")
plt.title("Avg End-to-End Delay vs Speed of Nodes")
plt.grid(True)

# Adjust layout
plt.tight_layout()

# Save the plots as a file or show them
plt.savefig("aodv_speeds_metrics.png")  # Save as an image file
plt.show()