# Provides the shopping list for spaghetti alla carbonara

```mermaid    
sequenceDiagram
    client->>+cooking-assistant: POST 
    cooking-assistant->>+recipe-agent: task
    recipe-agent->>+recipes-mcp: tools/call search_recipes
    recipes-mcp-->>-recipe-agent: 
    recipe-agent-->>-cooking-assistant: 
    cooking-assistant->>+shopping-list-agent: task
    shopping-list-agent->>+shopping-list-agent: format-shopping-list
    shopping-list-agent-->>-shopping-list-agent: 
    shopping-list-agent-->>-cooking-assistant: 
    cooking-assistant-->>-client: 200
    participant client as Client
    participant cooking-assistant as Cooking Assistant
    participant recipe-agent as Recipe Agent
    participant recipes-mcp as Recipes Mcp
    participant shopping-list-agent as Shopping List Agent

```