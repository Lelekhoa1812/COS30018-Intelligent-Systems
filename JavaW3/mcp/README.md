# TicTacToe 3x3 AI Agent Player

Fusing Gemini Flash 2.5 Lite to act as AI Agent to play the classic Tic Tac Toe game.

## **Run command:**
```bash
cd path/to/mcp
export GOOGLE_API_KEY="your-key"
mvn clean package -DskipTests
mvn exec:java     
```

## **Screenshots:**
1. Mode Selection: 
<img src="imgsrc/mode.png" alt="Mode Selection" style="width: 80%; max-width: 1000px;">
2. Game Play:
<img src="imgsrc/gameplay.png" alt="Mode Selection" style="width: 80%; max-width: 1000px;">


## **Strategies:**
* **Always wins/blocks** when a 1-move tactic exists
* Detects and prioritises **open-4, closed-4, open-3, double-threat (two open-3s)**, etc.
* Scores every legal move, prunes to a **smart shortlist**, then asks Gemini to choose among the best (while still enforcing `"row col"` output)
* Falls back to deterministic top score if the LLM output is invalid


## **Advantages to TradLLM**
* **Immediate win/block**: never misses a 1-move win; always blocks opponent’s 1-move win.
* **Threat awareness**: prioritises **open-4**, **closed-4**, **open-3**, and **forks** (two open-3s).
* **Positioning**: prefers center/adjacent play and extending the **longest live chain**.
* **Pruning**: considers only empty cells near stones (or center on first move).
* **LLM use**: limited to choosing among top-K high-quality options with a strict output contract; **deterministic fallback** otherwise.

## **Requirements**
* Java21
* Maven 3.9.11 at least
