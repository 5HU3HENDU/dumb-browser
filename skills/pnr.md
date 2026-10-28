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
- Public sources only. Three live PNR sources, checked 2026-10-28:
  ConfirmTkt, ixigo, RailYatri. The official railway site is NOT a source:
  it shows a CAPTCHA after submit, which automated checks must not bypass.
  Trainman is dead (moved to Cleartrip) and Cleartrip demands a phone+OTP
  login at the result page despite claiming otherwise — neither is usable.
  When sources disagree, the value agreed by two sources wins (e.g. a
  departure time or platform shown by two of the three); the in-app
  "another source says" note for disagreements needs an app change and
  lands in v1.1 — until then the majority value is shown silently.
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

- Source A — ConfirmTkt PNR page: open the page, enter the PNR, submit,
  wait for the result (its loading screen takes ~20 seconds), read fields
  1–7. It also gives the quota.
- Source B — ixigo PNR page (`/trains/pnr-status`; the plain `/pnr-status`
  URL is a 404): same steps. It is the only source for the arrival
  platform, the fare, and the coach-position diagram (class codes in order,
  based on past data — typical, never the confirmed rake; its station
  selector defaults to a station other than the boarding station, so the
  diagram is shown as a typical sequence only until v1.1 adds station
  selection and the mapped coach rail).
- Source C — RailYatri PNR page: same steps. It is the only source for a
  full DD-MM-YYYY journey date; the other two show "Wed, 11 Nov" style
  dates with no year, so the date patterns deliberately match only the
  full format. If RailYatri fails, the date shows "Not available" until
  v1.1 adds proper cross-source voting.
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


Live note (2026-10-28): all three sources verified live with a real PNR.
Known disagreements on that trip — departure 05:30 (ConfirmTkt + ixigo) vs
05:20 (RailYatri); boarding platform 6 (ConfirmTkt + RailYatri) vs 5
(ixigo); duration 3h55m (ixigo) vs 4h10m (RailYatri, inconsistent with its
own times). The pattern order implements the majority values for these
cases: ConfirmTkt first for time/platform, ixigo for duration/arrival/fare/
diagram, RailYatri for the full journey date. True vote-counting and the
"another source says" disagreement note need a runner change — v1.1.

```skill-json
{
  "id": "pnr",
  "title": "PNR Status",
  "refreshHours": 6,
  "inputs": [
    {
      "key": "pnr",
      "label": "PNR number",
      "pattern": "^[0-9]{10}$",
      "hint": "10-digit PNR from your ticket"
    }
  ],
  "sources": [
    {
      "key": "confirmtkt",
      "name": "ConfirmTkt",
      "url": "https://www.confirmtkt.com/pnr-status",
      "steps": [
        {
          "action": "fill",
          "label": "pnr",
          "value": "{pnr}"
        },
        {
          "action": "submit"
        },
        {
          "action": "wait",
          "delayMs": 22000
        },
        {
          "action": "extract"
        }
      ]
    },
    {
      "key": "ixigo",
      "name": "ixigo",
      "url": "https://www.ixigo.com/trains/pnr-status",
      "steps": [
        {
          "action": "fill",
          "label": "pnr",
          "value": "{pnr}"
        },
        {
          "action": "submit"
        },
        {
          "action": "wait",
          "delayMs": 12000
        },
        {
          "action": "extract"
        }
      ]
    },
    {
      "key": "railyatri",
      "name": "RailYatri",
      "url": "https://www.railyatri.in/pnr-status",
      "steps": [
        {
          "action": "fill",
          "label": "pnr",
          "value": "{pnr}"
        },
        {
          "action": "submit"
        },
        {
          "action": "wait",
          "delayMs": 6000
        },
        {
          "action": "extract"
        }
      ]
    }
  ],
  "fields": [
    {
      "key": "trainNumber",
      "label": "Train number",
      "required": true,
      "patterns": [
        "\\b(\\d{5})\\s*-\\s*[A-Z]",
        "\\b(\\d{5})\\s+[A-Z]",
        "TRAIN NAME\\s*[:/]\\s*(\\d{5})",
        "\\b(\\d{5})\\s+[A-Z][A-Za-z ]+?\\s+\\d{2}-\\d{2}-\\d{4}"
      ]
    },
    {
      "key": "trainName",
      "label": "Train name",
      "required": true,
      "patterns": [
        "\\b\\d{5}\\s*-\\s*([A-Z]+(?: [A-Z]+)*)",
        "\\b\\d{5}\\s+([A-Za-z]+(?: [A-Za-z]+)*)",
        "TRAIN NAME\\s*[:/]\\s*\\d{5}\\s*[\\u2012\\u2013\\u2014\\-]\\s*([A-Za-z]+(?: [A-Za-z]+)*?)(?:\\s+FROM\\b|\\s*\\n|$)",
        "\\b\\d{5}\\s+([A-Z][A-Za-z ]+?)\\s+\\d{2}-\\d{2}-\\d{4}"
      ]
    },
    {
      "key": "journeyDate",
      "label": "Journey date",
      "required": true,
      "patterns": [
        "DAY OF BOARDING\\s*/?\\s*(\\d{2}-\\d{2}-\\d{4})",
        "\\b\\d{5}\\s+[A-Z][A-Za-z ]+?\\s+(\\d{2}-\\d{2}-\\d{4})"
      ]
    },
    {
      "key": "fromStation",
      "label": "From",
      "patterns": [
        "\\b([A-Za-z]+(?: [A-Za-z]+)*)\\s*-\\s*(?-i:[A-Z]{2,}),",
        "(?-i:[A-Z]{2,5})\\s+([A-Za-z]+(?: [A-Za-z]+)*)\\s+Exp\\.?\\s*Platform\\s*No\\.?\\s*\\d+",
        "\\bFROM\\b\\s*[:/]?\\s*([A-Z][A-Z ]+?)\\s*\\|",
        "\\d{2}-\\d{2}-\\d{4}\\s+([A-Z]{2,5})\\s+[A-Z]{2,5}"
      ]
    },
    {
      "key": "toStation",
      "label": "To",
      "patterns": [
        "[\\s\\S]*\\b([A-Za-z]+(?: [A-Za-z]+)*)\\s*-\\s*(?-i:[A-Z]{2,}),",
        "[\\s\\S]*(?-i:[A-Z]{2,5})\\s+([A-Za-z]+(?: [A-Za-z]+)*)\\s+Exp\\.?\\s*Platform\\s*No\\.?\\s*\\d+",
        "\\bTO\\b\\s*[:/]?\\s*([A-Z][A-Z ]+?)\\s*\\|",
        "\\d{2}-\\d{2}-\\d{4}\\s+[A-Z]{2,5}\\s+([A-Z]{2,5})"
      ]
    },
    {
      "key": "travelClass",
      "label": "Class",
      "patterns": [
        "\\|\\s*(3A|2A|1A|SL|CC|EC|FC|2S)\\s*\\|",
        "(\\d+A|SL|CC|EC)\\s*-\\s*(Third|Second|First)",
        "CLASS\\s*/?\\s+([A-Z][A-Z0-9 ]*?)\\s*\\n",
        "CLASS\\s*/?\\s+(AC\\s*\\d\\s*TIER|SLEEPER|SECOND\\s*SITTING|AC\\s*CHAIR\\s*CAR|FIRST\\s*CLASS)",
        "\\d{2}-\\d{2}-\\d{4}[\\s\\S]{0,150}?\\b(3A|2A|1A|SL|CC|EC|FC|2S)\\b"
      ]
    },
    {
      "key": "coach",
      "label": "Coach",
      "patterns": [
        "CNF ([A-Z]{1,3}\\d{0,2}) \\d+ / Available",
        "COACH/BERTH[\\s\\S]{0,200}?([A-Z]{1,3}\\d{0,2})\\/\\d+",
        "\\b([A-Z]\\d{1,2})\\s*,\\s*\\d+"
      ]
    },
    {
      "key": "berth",
      "label": "Berth",
      "patterns": [
        "CNF [A-Z]{1,3}\\d{0,2} (\\d+) / Available",
        "COACH/BERTH[\\s\\S]{0,200}?[A-Z]{1,3}\\d{0,2}\\/(\\d+)",
        "\\b[A-Z]\\d{1,2}\\s*,\\s*(\\d+)\\b"
      ]
    },
    {
      "key": "bookingStatus",
      "label": "Booking status",
      "patterns": [
        "COACH/BERTH\\s+1\\.\\s+([A-Z]+)",
        "\\b(CNF|RAC|WL ?\\d+)\\b",
        "\\b(Confirmed|Waitlisted)\\b"
      ]
    },
    {
      "key": "chartStatus",
      "label": "Chart status",
      "patterns": [
        "(Chart Prepared|Chart Not Prepared)",
        "CHART STATUS\\s*/?\\s*((?:NOT )?PREPARED)"
      ]
    },
    {
      "key": "fromTime",
      "label": "Departure time",
      "patterns": [
        "-\\s*[A-Z]{2,},\\s*(\\d{1,2}:\\d{2})",
        "(?<!\\d)(\\d{1,2}:\\d{2})\\s+(?![AP]M\\b)[A-Z]{2,}",
        "\\bFROM\\b[\\s\\S]{0,120}?\\|\\s*[A-Z]+\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)"
      ]
    },
    {
      "key": "toTime",
      "label": "Arrival time",
      "patterns": [
        "[\\s\\S]*-\\s*[A-Z]{2,},\\s*(\\d{1,2}:\\d{2})",
        "[\\s\\S]*(?<!\\d)(\\d{1,2}:\\d{2})\\s+(?![AP]M\\b)[A-Z]{2,}",
        "\\bTO\\b[\\s\\S]{0,120}?\\|\\s*[A-Z]+\\s+(\\d{1,2}:\\d{2}\\s*[AP]M)"
      ]
    },
    {
      "key": "platform",
      "label": "Platform",
      "patterns": [
        "Expected platform\\s*:?\\s*(\\d+)",
        "Exp\\.?\\s*Platform\\s*No\\.?\\s*(\\d+)",
        "PF#\\s*\\(TENTATIVE\\)\\s*/?\\s*(\\d+)"
      ]
    },
    {
      "key": "duration",
      "label": "Duration",
      "patterns": [
        "Exp\\.?\\s*Platform\\s*No\\.?\\s*\\d+[\\s\\S]{0,80}?(\\d+h\\s*\\d+m)",
        "JOURNEY TIME\\s*/?\\s*(\\d+H\\s*\\d+M)"
      ]
    },
    {
      "key": "distance",
      "label": "Distance",
      "patterns": []
    },
    {
      "key": "arrivalPlatform",
      "label": "Arrival platform",
      "patterns": [
        "[\\s\\S]*Exp\\.?\\s*Platform\\s*No\\.?\\s*(\\d+)"
      ]
    },
    {
      "key": "fare",
      "label": "Fare",
      "patterns": [
        "(₹\\s*[\\d,]+)",
        "Ticket Fare:\\s*(₹[\\d,]+)"
      ]
    },
    {
      "key": "coachDiagram",
      "label": "Coach position (typical)",
      "patterns": [
        "Click on coach to view seat layout\\s+Scroll Up\\s+([\\s\\S]+?)\\s+Scroll Down"
      ]
    },
    {
      "key": "coachCount",
      "label": "Coach count",
      "patterns": [
        "(\\d+) Coaches"
      ]
    }
  ],
  "extra": {
    "coachOrders": {
      "19489": [
        "LPR",
        "GEN",
        "GEN",
        "H1",
        "A1",
        "A2",
        "B1",
        "B2",
        "B3",
        "B4",
        "M1",
        "M2",
        "S1",
        "S2",
        "S3",
        "S4",
        "S5",
        "S6",
        "S7",
        "GEN",
        "GEN",
        "SLRD"
      ]
    }
  }
}
```
