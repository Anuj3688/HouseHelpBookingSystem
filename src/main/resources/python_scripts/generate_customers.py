import json
import random

first_names = [
    "Aarav", "Ananya", "Bhavna", "Chetan", "Divya", "Esha", "Farhan", "Gautam", "Harini", "Ishaan",
    "Jyoti", "Karan", "Lakshmi", "Manish", "Neha", "Omkar", "Pooja", "Rahul", "Sanya", "Tanvi",
    "Utkarsh", "Varun", "Yash", "Zoya", "Aditya", "Bhumika", "Chirag", "Deepika", "Ekta", "Ganesh"
]
last_names = [
    "Sharma", "Patel", "Kumar", "Nair", "Gupta", "Khan", "Joshi", "Rao", "Verma", "Singh",
    "Malhotra", "Iyer", "Reddy", "Kapoor", "Deshmukh", "Chawla", "Bhasin", "Pillai", "Kulkarni", "Mehta"
]
localities = ["Koramangala", "Indiranagar", "Whitefield", "HSR_Layout", "Jayanagar", "BTM_Layout", "Hebbal", "Electronic_City"]

customers = []
for i in range(1, 101):
    fname = random.choice(first_names)
    lname = random.choice(last_names)
    loc = random.choice(localities)
    customers.append({
        "name": f"{fname} {lname}",
        "address": f"Flat {100 + (i % 800)}, Block {chr(65 + (i % 6))}, {loc}, Bangalore"
    })

with open("customers_seed.json", "w") as f:
    json.dump(customers, f, indent=2)

print(f"Generated {len(customers)} customers in customers_seed.json")
