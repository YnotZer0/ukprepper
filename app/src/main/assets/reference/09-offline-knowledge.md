# Offline knowledge: Project NOMAD and alternatives

Everything below must be downloaded BEFORE you need it. Once the internet is gone, you can only use what you already have.

## On the phone (lowest power - do this first)
- Kiwix: an offline reader for Wikipedia and other libraries ("ZIM" files). Good ones to download:
  - Wikipedia English (the "nopic" edition is much smaller)
  - WikiMed (medical)
  - Wikivoyage
  - iFixit (repairs)
  - Practical Stack Exchange sites
- Organic Maps or OsmAnd, with the Great Britain maps downloaded, for navigation without data.
- Store large files on the phone itself, and update them every few months.

## Project NOMAD on the HP ZBook
NOMAD is a free, open-source offline server. It bundles a Kiwix library, offline maps, educational content, notes and optional local AI (Ollama), and serves them to other devices over local Wi-Fi.
- Strength: one hub the whole household can browse from phones, with local AI to search and summarise.
- Cost: the ZBook draws a lot of power, especially while Ollama is answering. Read the real draw from the AFERIY display and enter it in Energy & fuel.
- NOMAD's content defaults lean towards the US. Add UK-relevant material yourself: GB maps, NHS-style health guidance, UK plant and fungi guides.
- Run it in planned sessions, e.g. 30 minutes at midday when the sun is charging, rather than leaving it on.

! Local AI can be confidently wrong. For anything medical or safety-related, check the answer against a source document in Kiwix (WikiMed) before acting on it.

## Lighter alternatives to running NOMAD
- kiwix-serve on the ZBook alone: much lighter than the full NOMAD stack, and serves the same libraries to your phone over Wi-Fi.
- Internet-in-a-Box on a Raspberry Pi: a few watts instead of tens of watts, with similar libraries. It's worth considering as a low-power always-on hub.
- Kiwix on the phone alone: no hub, but almost no extra power.

## Checklist (do now, while online)
1. Install Kiwix and download WikiMed and Wikipedia (nopic) on the phone.
2. Install Organic Maps and download Great Britain.
3. Set up NOMAD on the ZBook, add GB maps, and test serving it to the phone with no internet connection.
4. Print this app's PDF and keep copies in the house and in the van.
