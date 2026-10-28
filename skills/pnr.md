# PNR skill

When the user adds a train PNR, the browser fetches the trip's details from
public railway sources and composes them into one view. This skill adapts
`schoolofadults.com/travel/train/train.md` — same fields, same rules. The
website's snapshot-link output is replaced by the in-app view; no link is
produced.

## Input

- PNR: exactly 10 digits. Nothing else is required from the user.

## Privacy and scope

- The PNR and trip belong only to the user who added them. Store on-device
  only; never send them to any server.
- Never include other passengers' names or details.
- Public sources only. Check official railway information first, then
  reputable live PNR and train sources. IRCTC PNR lookup may require a
  CAPTCHA; do not bypass or automate it. If the official PNR page is
  CAPTCHA-gated, use a live PNR page such as RailYatri and cross-check other
  fields with current reputable sources.
- Show the sources used and an "As of" timestamp in India Standard Time
  (IST) on every view.

## Fields to fetch (the view's content)

For the submitted PNR, look up and report, where available:

1. Train number and name.
2. Journey date.
3. Boarding and destination stations, with station codes.
4. Departure and arrival times.
5. Travel class.
6. Current coach and berth number(s), and booking status per berth.
7. Boarding and arrival platform numbers, and whether each is verified or
   projected.
8. Coach order from engine to rear, and the position of the traveller's
   coach in that order.
9. Journey duration and distance.

Rules:

- When sources disagree, prefer current live results and agreement across
  multiple current sources. Never silently treat an older result as current.
- If a value cannot be established, mark it UNVERIFIED (displayed in the
  view as "Not available") and say why.
- Distinguish a typical coach order from the actual rake for this journey.
  Never present a typical composition as confirmed actual placement.
- Do not include cancelled co-passengers.

## Sources and steps (machine block)

- Source A — official railway PNR enquiry: open the page, enter the PNR,
  submit, wait for the result, read fields 1–6.
- Source B — live PNR page (e.g. RailYatri): same steps. Use when Source A
  is CAPTCHA-gated or incomplete; cross-check fields against Source A.
- Source C — train running and coach sources: using the train number and
  journey date from A/B, fetch live running status, platform numbers, and
  coach order (fields 7–9).
- Pin the exact page URLs and selectors at implementation time; pages
  change. When selectors fail, fall back to the SLM adapter (read the page
  and find the fields by meaning) rather than failing the skill.
- View: render the fields natively as one screen — journey cards (boarding,
  journey details with platform and seat boxes, arrival), a numbered coach
  rail with the traveller's coach highlighted, a live-status strip, and a
  footer with the PNR, the As-of time, and the check-at-station note. The
  in-app view is the output.

## Refresh and follow-up

- Recheck at the start of the travel day, the day before departure, and on
  the journey day. Also recheck after chart preparation or when a reported
  change could affect the coach, berth, platform, delay, cancellation, or
  timing.
- Alert in-app promptly when a checked field changes. State what changed
  and cite the source.
- Remove an ended trip from the device 48 hours after its scheduled
  arrival.

## Worked example

Sample PNR 8752640193, train 19489 (Ahmedabad – Gorakhpur Express, coach B3,
berths 23–24 CNF). Example only; never hardcode its values.

## Runnable definition (read by the app)

The block below is what the app executes. Editing it — sources, steps,
field patterns, coach orders — changes the skill with no app update. The
view template is `pnr.view.html` in this same folder.

```skill-json
{
  "id": "pnr",
  "title": "PNR Status",
  "refreshHours": 6,
  "inputs": [
    {"key": "pnr", "label": "PNR number", "pattern": "^[0-9]{10}$", "hint": "10-digit PNR from your ticket"}
  ],
  "sources": [
    {
      "key": "official",
      "name": "Indian Railways (official)",
      "url": "https://www.indianrail.gov.in/enquiry/PNR/PnrEnquiry.html",
      "steps": [
        {"action": "fill", "label": "pnr", "value": "{pnr}"},
        {"action": "submit"},
        {"action": "wait", "delayMs": 6000},
        {"action": "extract"}
      ]
    },
    {
      "key": "railyatri",
      "name": "RailYatri",
      "url": "https://www.railyatri.in/pnr-status",
      "steps": [
        {"action": "fill", "label": "pnr", "value": "{pnr}"},
        {"action": "submit"},
        {"action": "wait", "delayMs": 6000},
        {"action": "extract"}
      ]
    }
  ],
  "fields": [
    {"key": "trainNumber", "label": "Train number", "required": true, "patterns": ["\\b(\\d{5})\\s+[A-Z][A-Za-z ]+?\\s+\\d{2}-\\d{2}-\\d{4}"]},
    {"key": "trainName", "label": "Train name", "required": true, "patterns": ["\\b\\d{5}\\s+([A-Z][A-Za-z ]+?)\\s+\\d{2}-\\d{2}-\\d{4}"]},
    {"key": "journeyDate", "label": "Journey date", "required": true, "patterns": ["\\b\\d{5}\\s+[A-Z][A-Za-z ]+?\\s+(\\d{2}-\\d{2}-\\d{4})"]},
    {"key": "fromStation", "label": "From", "patterns": ["\\d{2}-\\d{2}-\\d{4}\\s+([A-Z]{2,5})\\s+[A-Z]{2,5}"]},
    {"key": "toStation", "label": "To", "patterns": ["\\d{2}-\\d{2}-\\d{4}\\s+[A-Z]{2,5}\\s+([A-Z]{2,5})"]},
    {"key": "travelClass", "label": "Class", "patterns": ["\\d{2}-\\d{2}-\\d{4}[\\s\\S]{0,150}?\\b(3A|2A|1A|SL|CC|EC|FC|2S)\\b"]},
    {"key": "coach", "label": "Coach", "patterns": ["\\b([A-Z]\\d{1,2})\\s*,\\s*\\d+"]},
    {"key": "berth", "label": "Berth", "patterns": ["\\b[A-Z]\\d{1,2}\\s*,\\s*(\\d+)\\b"]},
    {"key": "bookingStatus", "label": "Booking status", "patterns": ["\\b(CNF|RAC|WL ?\\d+)\\b"]},
    {"key": "chartStatus", "label": "Chart status", "patterns": ["(Chart Prepared|Chart Not Prepared)"]},
    {"key": "fromTime", "label": "Departure time", "patterns": []},
    {"key": "toTime", "label": "Arrival time", "patterns": []},
    {"key": "platform", "label": "Platform", "patterns": []},
    {"key": "duration", "label": "Duration", "patterns": []},
    {"key": "distance", "label": "Distance", "patterns": []}
  ],
  "extra": {
    "coachOrders": {
      "19489": ["LPR", "GEN", "GEN", "H1", "A1", "A2", "B1", "B2", "B3", "B4", "M1", "M2", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "GEN", "GEN", "SLRD"]
    }
  }
}
```
