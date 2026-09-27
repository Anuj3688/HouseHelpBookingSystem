import json
import urllib.request

c_req = urllib.request.urlopen("http://localhost:8080/api/customers")
customers = json.loads(c_req.read().decode('utf-8'))

h_req = urllib.request.urlopen("http://localhost:8080/api/housekeeping/helpers")
helpers = json.loads(h_req.read().decode('utf-8'))

state = {
    "customers": customers,
    "helpers": helpers,
    "seededAt": "2026-09-27T08:32:00Z"
}

with open("/Users/anujtiwari/Desktop/HoseHelpBookingSystem/src/main/resources/python_scripts/active_system_state.json", "w") as f:
    json.dump(state, f, indent=2)

print(f"Captured active_system_state.json with {len(customers)} customers and {len(helpers)} helpers.")
