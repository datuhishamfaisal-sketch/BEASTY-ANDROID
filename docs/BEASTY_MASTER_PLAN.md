# BEASTY Master Plan

**Status:** Long-term product vision and implementation roadmap  
**Product:** BEASTY Android AI assistant  
**Owner preference:** Full Autonomous AI with all planned modules enabled.  
**Default interaction:** Smart Mode. Fast Mode and Deep Mode remain selectable.

This document is the durable source of truth for BEASTY's intended capabilities. Features listed here are planned unless explicitly marked implemented in the app.

## 1. Core identity and operating principles
- BEASTY is one Android app combining a personal AI assistant, autonomous work agent, and business opportunity hub.
- Use Gemini and Groq as AI providers. Keep provider selection/routing flexible and provide graceful fallback when one provider fails.
- Smart Mode is the default: fast while still complete and intelligent. Fast Mode gives concise direct answers. Deep Mode performs more detailed reasoning, research, and planning.
- Communicate naturally in the user's language, including Malay. Voice replies should be clear, natural, and simple.
- Remember durable, non-sensitive user preferences, facts, project context, and decisions. Let the user inspect, correct, and delete memories.
- Keep project roadmap/BEASTY's own design knowledge separate from personal user memories and business/client records.
- Work autonomously on research, analysis, drafting, planning, coding, and preparation. Show progress and report limitations honestly.

## 2. Voice-first assistant and Android operation
- Hands-free wake-word activation, with the intended product wake phrase **Hey BEASTY**. The currently tested Android wake-word integration uses **Hey Jarvis**; migrate to a Hey BEASTY model when a suitable model is available and validated.
- Support voice conversations without repeating the wake phrase for every turn, with a clear listening/thinking/speaking state.
- Target operation while BEASTY is open, on the Android home screen, inside other apps, and with the screen locked, subject to Android permissions, foreground microphone service rules, and device restrictions.
- Include explicit microphone permission, visible listening notification/status, user-controlled enable/disable and pause/resume, battery-conscious operation, and robust recovery from recognition/service errors.
- Sensitive actions should require device unlock and explicit confirmation.

## 3. Persistent memory and context
- Automatic Memory: detect and save useful durable, non-sensitive facts and preferences from normal conversation, not only fixed trigger phrases.
- Update or replace outdated facts instead of accumulating contradictions.
- Separate personal memory, conversation history, project plan, client/lead records, and generated work files.
- Provide commands to show, correct, forget selected facts, and clear memory.
- Preserve memory through normal app updates. Add a user-controlled encrypted export/backup and restore path so data can be recovered after uninstall or device migration; never promise Android app-private data survives uninstall without backup.
- Explain clearly when relevant memory is included in an AI request to Gemini/Groq.

## 4. Personal AI assistant
- General question answering, explanations, translation, writing, calculations, planning, reminders, and task organization.
- Personal productivity support: notes, schedules, checklists, follow-ups, and summaries.
- Context-aware assistance using approved memories and current conversation.
- Prepare actions and ask for confirmation before external or irreversible operations.

## 5. Business Hub and autonomous business modules
All modules are part of the intended product; build them progressively and connect them through shared research, records, and reporting.

### Business Hunter
- Discover business opportunities, niches, demand gaps, potential customers, and commercial leads.
- Evaluate opportunity fit, competition, startup difficulty, risks, and possible margins.

### Supplier Hunter
- Find manufacturers, factories, wholesalers, distributors, and sourcing agents worldwide.
- Compare supplier credibility, product range, minimum order quantity, indicative pricing, shipping, and contact channels.
- Flag unverified claims and distinguish verified evidence from estimates.

### Tender Hunter
- Find government and private-sector tenders, quotations, projects, and subcontracting opportunities.
- Filter by location, category, grade/licence, closing date, eligibility, and estimated value.
- Track tender documents, requirements, deadlines, and next actions.

### Market Research and Trend Hunter
- Research demand, customer segments, competitors, market pricing, product trends, and emerging opportunities.
- Cite sources, dates, and confidence; separate facts from estimates.
- Generate concise or deep market reports.

### Auto Business Planner
- Turn an opportunity into a practical business plan.
- Estimate capital, operating costs, pricing, cash flow, break-even, risks, and launch steps.
- Allow the user to revise assumptions and compare scenarios.

### Lead Manager / CRM
- Save and organize customers, prospects, suppliers, buyers, and tender contacts.
- Track source, contact details, interaction history, status, next follow-up, and notes.
- Search, edit, export, and summarize lead records with user control.

### AI Negotiator
- Prepare quotations, offers, supplier enquiries, follow-up messages, and negotiation strategies.
- Compare offer terms and suggest counteroffers.
- Draft messages autonomously, but never send customer/supplier messages without user approval.

## 6. Creator studio
### BEASTY Web Creator
- Accept website requests by voice or text.
- Generate website structure, content, and code; preview, revise, and prepare deployment.
- Explain hosting/domain choices and deployment steps.
- Publishing, purchasing domains/hosting, or making a website public requires explicit user approval.

### BEASTY AI Creator (App Creator)
- Accept Android app creation requests by voice or text.
- Plan the app, generate and revise source code, diagnose errors, run builds, and prepare installable APK artifacts when the build succeeds.
- Show project files, build status, errors, and test results clearly.
- Installing, publishing, or distributing externally requires user approval.

### Video Creator
- Help prepare video concepts, scripts, storyboards, captions, and production assets; add generation/export capabilities as supported.

## 7. Autonomous execution and approval boundaries
- Full Autonomous AI is the chosen operating direction: BEASTY should plan multi-step tasks, research, analyze, create drafts/files, and carry out safe in-app work without asking at every small step.
- Always request explicit approval before payments, purchases, financial transactions, publishing/deployment, sending messages/emails, deleting user files/data, or other external/irreversible actions.
- Require device unlock plus confirmation for sensitive actions.
- Never fabricate research, supplier verification, tender status, build success, or completed actions. Show evidence, uncertainty, and errors.
- Keep an activity history for tasks, approvals, and outcomes.

## 8. Interface and user experience
- Maintain a clear dashboard for assistant status, voice controls, modes, business modules, recent tasks, and memory controls.
- Make module status visible: planned, in progress, available, or needs setup.
- Use simple natural-language interaction first, with optional structured controls for complex workflows.
- Make permissions, data use, memory, and background listening transparent and user-controlled.

## 9. Delivery roadmap — revised priority (2026-11-03)
The user's current instruction is to prioritize major, usable creation and business capabilities before polishing smaller features such as Advanced Memory. Do not spend the next development phase on memory enhancements unless required to unblock a priority module.

1. Preserve and stabilize the existing Android foundation: Gemini/Groq chat and fallback, Smart/Fast/Deep modes, speech input/output, and wake-word service. Do not regress working features.
2. Build **BEASTY Web Creator** into a usable workflow: voice/text request, website generation, editable code, live preview, iterative revisions, and deployment preparation. Require approval before public deployment or paid hosting/domain actions.
3. Build **BEASTY AI Creator (App Creator)** into a usable workflow: app planning, source-code generation/editing, debugging, build execution, test/build status, and APK artifact delivery when a build succeeds. Require approval before external publishing/distribution.
4. Build **Business Hunter** into a usable research workflow: discover opportunities, products and demand, identify target customers/leads, assess competition, estimate costs/margins/risks, and produce sourced reports.
5. Build **Supplier Hunter**: discover manufacturers and wholesalers, compare MOQ/prices/shipping, capture contact channels, and label verification evidence clearly.
6. Build **Tender Hunter**: discover government/private tenders and quotations, filter by location/category/licence/grade/deadline, and track eligibility and required documents.
7. Connect these capabilities through shared research, saved projects, business records, and a simple dashboard with clear status labels.
8. Add Market Research/Trend Hunter, Auto Business Planner, Lead Manager/CRM, and AI Negotiator to complete the Business Hub.
9. Return to smaller improvements: Advanced Memory review/edit, backup/restore, Personal Assistant productivity, and other refinements.
10. Expand Video Creator and the wider autonomous execution engine, then harden approvals, audit history, security, and performance.

Priority rule: deliver and test working end-to-end capability for Web Creator, AI Creator, and Business Hunter first. A documented plan, menu item, or generated mockup does not count as an implemented module.

## 10. Current implementation notes
At the time this plan was added, the Android app has working chat with Gemini/Groq fallback, selectable Smart/Fast/Deep modes, manual speech input, text-to-speech, an openWakeWord-based background voice service, continuous voice conversation, and cue-based local Automatic Memory. The user has confirmed the wake-word/voice flow works in foreground, on the home screen, in other apps, and while locked. The user has confirmed a basic memory fact can be saved and recalled.

The modules in this plan are not all implemented merely because they are documented here. Update this section and module status as development progresses.
