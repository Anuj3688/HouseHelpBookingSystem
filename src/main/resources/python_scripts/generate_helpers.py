import json
import random

first_names = [
    "Rajesh", "Sunita", "Amit", "Priya", "Suresh", "Kavita", "Vijay", "Meena", "Ramesh", "Geeta",
    "Sanjay", "Anita", "Deepak", "Sunil", "Asha", "Ravi", "Usha", "Manoj", "Rekha", "Pramod"
]
last_names = [
    "Kumar", "Devi", "Prasad", "Lal", "Ram", "Yadav", "Gowda", "Shinde", "Patil", "Swamy"
]
genders = ["FEMALE", "MALE", "OTHER"]
localities_pool = ["Koramangala", "Indiranagar", "Whitefield", "HSR_Layout", "Jayanagar", "BTM_Layout", "Hebbal", "Electronic_City"]
skills_pool = ["CLEANING", "COOKING", "CHILD_CARE", "ELDER_CARE", "LAUNDRY", "DISH_WASHING"]

helpers = []
for i in range(1, 501):
    fname = random.choice(first_names)
    lname = random.choice(last_names)
    # Pick 1-3 distinct localities
    num_locs = random.randint(1, 3)
    locs = random.sample(localities_pool, num_locs)
    # Pick 1-3 distinct skills
    num_skills = random.randint(1, 3)
    skills = random.sample(skills_pool, num_skills)
    rate = float(random.randint(15, 50) * 10) # 150 to 500
    phone = f"9988{i:06d}"
    
    helpers.append({
        "name": f"{fname} {lname}_{i}",
        "phone": phone,
        "gender": random.choice(genders),
        "localities": locs,
        "skills": skills,
        "hourlyRate": rate,
        "governmentIdProof": f"GOVT-ID-{i:04d}"
    })

with open("helpers_seed.json", "w") as f:
    json.dump(helpers, f, indent=2)

print(f"Generated {len(helpers)} helpers in helpers_seed.json")
