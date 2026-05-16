# Agent card is discoverable

```mermaid    
sequenceDiagram
    client->>+cooking-assistant: GET .well-known/agent-card.json
    cooking-assistant-->>-client: 200
    participant client as Client
    participant cooking-assistant as Cooking Assistant

```