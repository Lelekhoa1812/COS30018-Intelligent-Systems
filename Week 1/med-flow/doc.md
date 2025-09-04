# CPG Chatbot Architecture

Draw a full system architecture diagram for an on-premise Vietnamese-language AI chatbot that answers sales analytics questions. Use these components and flows:

1. User Interface (React, Vietnamese input)
→ Sends questions to Orchestrator via REST (port 8000)
2. Orchestrator (FastAPI service, core logic)
→ Receives question
→ Retrieves schema info (either from static JSON or optional vector store via port 9200)
→ Sends schema + question to LLM via REST/gRPC (port 11434)
→ Receives SQL query
→ (Optionally) reranks with Jina reranker or mpnet reranker via REST (port 9000)
→ Executes SQL on MySQL (port 3306) and/or Oracle DB (port 1521)
→ Returns query result
→ Sends data + original question to LLM for answer generation
→ Returns final answer to UI
3. LLM Service
    - Runs Qwen 3.0 (14B) on a GPU server
    - Accepts REST/gRPC requests from orchestrator
    - Two roles: (a) SQL generation, (b) Answer generation (Vietnamese)
4. MySQL + Oracle Databases
    - Store structured sales data
    - Accept SQL queries from orchestrator
5. (Optional) Schema Vector DB (Chroma or Qdrant)
    - Embeds table metadata
    - Helps find relevant schema based on user intent
6. (Optional) SQL Reranker
    - Jina multilingual reranker or similar
    - Scores top-k SQL queries for accuracy
Use arrows to indicate data flow. Label all ports and data directions. Distinguish REST, SQL, and vector search flows. Label GPU/CPU needs. This is a closed, local system — no cloud APIs or external calls.

