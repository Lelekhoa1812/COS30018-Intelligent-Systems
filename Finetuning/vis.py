#!/usr/bin/env python
"""
vis.py
Usage:
  python3 vis.py \
      --log-file medalpaca_training_log.json \
      --out-dir plots --smooth 5

- Reads a HF-style list[dict] JSON log (train/eval/train_runtime entries).
- Produces PNGs and a summary.txt.
"""

import json, os, argparse, math
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

def rolling_mean(y, w):
    if w is None or w <= 1: return np.array(y, dtype=float)
    y = np.asarray(y, dtype=float)
    if len(y) < w: return y
    c = np.convolve(y, np.ones(w)/w, mode="valid")
    pad = np.full(w-1, np.nan)
    return np.concatenate([pad, c], axis=0)

def load_log(path: Path) -> pd.DataFrame:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    df = pd.DataFrame(data)
    # Ensure expected columns exist (filled with NaN if missing)
    for c in ["step","epoch","loss","eval_loss","learning_rate","grad_norm",
              "eval_runtime","eval_samples_per_second","eval_steps_per_second",
              "train_runtime","train_steps_per_second","train_samples_per_second",
              "train_loss"]:
        if c not in df.columns: df[c] = np.nan
    # Keep only rows that have a step (some train summary rows might, some not)
    if df["step"].isna().all():
        # Fall back: add a monotonic step
        df["step"] = np.arange(1, len(df)+1)
    return df

def find_best_eval(df: pd.DataFrame):
    ev = df.dropna(subset=["eval_loss"])
    if ev.empty: return None
    best_idx = ev["eval_loss"].idxmin()
    row = ev.loc[best_idx]
    return {
        "step": int(row["step"]),
        "epoch": float(row["epoch"]) if not math.isnan(row["epoch"]) else None,
        "eval_loss": float(row["eval_loss"])
    }

def plateau_after(df: pd.DataFrame, patience_evals: int = 2):
    """Return a suggested 'stop at best' step if no improvement after N evals."""
    ev = df.dropna(subset=["eval_loss"]).reset_index(drop=True)
    if ev.empty: return None
    best = ev["eval_loss"].min()
    best_pos = int(ev["eval_loss"].idxmin())
    # Did we see N evals after best without improvement?
    tail = ev.loc[best_pos+1:]
    if len(tail) >= patience_evals and (tail["eval_loss"] <= best + 1e-12).sum() == 0:
        return int(ev.loc[best_pos, "step"])
    return None

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log-file", required=True)
    ap.add_argument("--out-dir", default="plots")
    ap.add_argument("--smooth", type=int, default=5, help="rolling window for train loss curve")
    args = ap.parse_args()

    outdir = Path(args.out_dir); outdir.mkdir(parents=True, exist_ok=True)
    df = load_log(Path(args.log_file))

    # --- Split views
    train_df = df.dropna(subset=["loss"])
    eval_df  = df.dropna(subset=["eval_loss"])
    # Some runs log final train aggregates
    agg = df.dropna(subset=["train_runtime"])

    # --- Best eval + plateau
    best = find_best_eval(df)
    stop_suggestion = plateau_after(df, patience_evals=2)

    # --- 1) Loss plot
    plt.figure(figsize=(10,6))
    if not train_df.empty:
        x = train_df["step"].astype(int).values
        y = train_df["loss"].astype(float).values
        ys = rolling_mean(y, args.smooth)
        plt.plot(x, ys, label=f"train loss (smooth={args.smooth})", linewidth=2)
    if not eval_df.empty:
        plt.scatter(eval_df["step"], eval_df["eval_loss"], label="eval loss", s=30, marker="o", color="tab:orange")
        if best:
            plt.axvline(best["step"], color="tab:orange", linestyle="--", alpha=0.5)
            plt.annotate(f"best eval={best['eval_loss']:.4f}\n(step {best['step']})",
                         xy=(best["step"], best["eval_loss"]),
                         xytext=(best["step"], best["eval_loss"]+0.01),
                         arrowprops=dict(arrowstyle="->", color="tab:orange"))
    plt.xlabel("step"); plt.ylabel("loss")
    plt.title("Training vs Evaluation Loss")
    plt.grid(alpha=0.2); plt.legend()
    plt.tight_layout()
    plt.savefig(outdir / "loss_curve.png", dpi=150)
    plt.close()

    # --- 2) LR + Grad Norm dual-axis
    plt.figure(figsize=(10,6))
    ax1 = plt.gca()
    ax2 = ax1.twinx()
    if not df["learning_rate"].dropna().empty:
        ax1.plot(df["step"], df["learning_rate"], color="tab:blue", label="learning rate", linewidth=2)
        ax1.set_ylabel("learning rate", color="tab:blue")
    if not df["grad_norm"].dropna().empty:
        ax2.plot(df["step"], df["grad_norm"], color="tab:red", label="grad norm", linewidth=1.5, alpha=0.8)
        ax2.set_ylabel("grad norm", color="tab:red")
    ax1.set_xlabel("step")
    ax1.set_title("LR & Gradient Norm vs Step")
    ax1.grid(alpha=0.2)
    plt.tight_layout()
    plt.savefig(outdir / "lr_gradnorm.png", dpi=150)
    plt.close()

    # --- 3) Eval throughput “panel”
    txt_lines = []
    if not eval_df.empty:
        rt = eval_df["eval_runtime"].dropna()
        sps = eval_df["eval_samples_per_second"].dropna()
        eps = eval_df["eval_steps_per_second"].dropna()
        if not rt.empty:
            txt_lines.append(f"Avg eval_runtime: {rt.mean():.1f} s  (min {rt.min():.1f}, max {rt.max():.1f})")
        if not sps.empty:
            txt_lines.append(f"Avg eval samples/s: {sps.mean():.3f}")
        if not eps.empty:
            txt_lines.append(f"Avg eval steps/s:   {eps.mean():.3f}")
    if not agg.empty:
        trt = agg["train_runtime"].dropna()
        tss = agg["train_steps_per_second"].dropna()
        tps = agg["train_samples_per_second"].dropna()
        if not trt.empty:
            hrs = trt.iloc[-1] / 3600.0
            txt_lines.append(f"Train runtime (last): {trt.iloc[-1]:.1f} s (~{hrs:.2f} h)")
        if not tss.empty:
            txt_lines.append(f"Train steps/s (last): {tss.iloc[-1]:.3f}")
        if not tps.empty:
            txt_lines.append(f"Train samples/s (last): {tps.iloc[-1]:.3f}")

    panel_text = "\n".join(txt_lines) if txt_lines else "No throughput stats in log."
    plt.figure(figsize=(8,4))
    plt.axis("off")
    plt.text(0.02, 0.98, "Throughput & Runtime", fontsize=14, fontweight="bold", va="top")
    plt.text(0.02, 0.85, panel_text, fontsize=12, va="top")
    if best:
        plt.text(0.02, 0.45, f"Best eval: {best['eval_loss']:.6f} @ step {best['step']}", fontsize=12, va="top")
    if stop_suggestion:
        plt.text(0.02, 0.35, f"Early-stop suggestion: stop near step {stop_suggestion} (patience=2)", fontsize=11, va="top")
    plt.tight_layout()
    plt.savefig(outdir / "throughput_panel.png", dpi=150)
    plt.close()

    # --- Summary file
    with open(outdir / "summary.txt", "w", encoding="utf-8") as f:
        f.write("Training log summary\n")
        f.write("====================\n\n")
        if best:
            f.write(f"Best eval_loss: {best['eval_loss']:.6f} at step {best['step']} (epoch {best['epoch']})\n")
        else:
            f.write("No eval_loss entries found.\n")
        if stop_suggestion:
            f.write(f"Early-stop suggestion (patience=2): stop near step {stop_suggestion}\n")
        if txt_lines:
            f.write("\n" + "\n".join(txt_lines) + "\n")
        # check for multiple evals at identical step (potential config drift)
        dupe = eval_df.groupby("step")["eval_loss"].apply(len)
        dupesteps = [int(s) for s, k in dupe.items() if k > 1]
        if dupesteps:
            f.write(f"\nWARNING: Multiple eval entries at same step(s): {dupesteps}. "
                    "Ensure consistent split/metrics across evaluations.\n")

if __name__ == "__main__":
    main()
