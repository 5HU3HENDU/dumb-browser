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
