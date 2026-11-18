#!/usr/bin/env python3
"""Optional desktop helper: local file picker and official font-source links. Requires Tk."""
import importlib.util
from pathlib import Path
import queue
import threading
import webbrowser
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

spec = importlib.util.spec_from_file_location("font_pack", Path(__file__).with_name("create-custom-font-pack.py"))
font_pack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(font_pack)

# Curated project pages, not automatic downloads or third-party mirrors.
SOURCES = {
    "Noto CJK (OFL; 中文)": "https://github.com/notofonts/noto-cjk",
    "Source Han Sans (OFL; 思源黑体)": "https://github.com/adobe-fonts/source-han-sans",
    "LXGW WenKai (OFL; 霞鹜文楷)": "https://github.com/lxgw/LxgwWenKai",
    "Source Sans (OFL; 西文)": "https://github.com/adobe-fonts/source-sans",
}


def main():
    root = tk.Tk()
    root.title("SHBUI 本地字体资源包")
    root.geometry("700x420")
    frame = ttk.Frame(root, padding=16)
    frame.pack(fill="both", expand=True)
    ttk.Label(frame, text="字体只在本机处理，不上传。支持独立 TrueType TTF。", wraplength=650).pack(anchor="w")
    regular, bold = tk.StringVar(), tk.StringVar()
    for label, variable in (("常规字体（必选）", regular), ("半粗字体（可选）", bold)):
        row = ttk.Frame(frame)
        row.pack(fill="x", pady=8)
        ttk.Label(row, text=label).pack(side="left")
        ttk.Entry(row, textvariable=variable).pack(side="left", fill="x", expand=True, padx=8)
        def choose(v=variable):
            path = filedialog.askopenfilename(filetypes=[("TrueType", "*.ttf")])
            if path:
                v.set(path)
        ttk.Button(row, text="选择文件…", command=choose).pack(side="right")
    version = tk.StringVar(value="1.21.1")
    ttk.Combobox(frame, state="readonly", values=list(font_pack.FORMATS), textvariable=version).pack(anchor="w")
    approved = tk.BooleanVar()
    ttk.Checkbutton(frame, text="我确认字体许可允许本地资源包用途（不代表可再分发）", variable=approved).pack(anchor="w", pady=10)
    status = tk.StringVar(value="生成后请在游戏资源包页面手动启用；禁用即可恢复默认字体。")
    events = queue.Queue()
    busy = False
    def poll():
        nonlocal busy
        try:
            ok, result = events.get_nowait()
        except queue.Empty:
            root.after(100, poll)
            return
        busy = False
        button.configure(state="normal")
        status.set("已生成；请在游戏资源包页面启用。" if ok else "生成失败，未启用字体。")
        if not ok:
            messagebox.showerror("生成失败", result)
        root.after(100, poll)
    def build():
        nonlocal busy
        if busy:
            return
        if not approved.get():
            messagebox.showwarning("需要许可确认", "请确认字体许可允许此用途。")
            return
        out = filedialog.asksaveasfilename(defaultextension=".zip", filetypes=[("Resource pack", "*.zip")])
        if not out:
            return
        values = (regular.get(), bold.get() or None, out, version.get(), approved.get())
        busy = True
        button.configure(state="disabled")
        status.set("正在生成本地资源包…")
        def worker():
            try:
                font_pack.create_pack(*values)
                events.put((True, ""))
            except Exception as error:
                events.put((False, str(error)))
        threading.Thread(target=worker, daemon=True).start()
    button = ttk.Button(frame, text="生成资源包…", command=build)
    button.pack(anchor="w")
    ttk.Label(frame, textvariable=status, wraplength=650).pack(anchor="w", pady=8)
    ttk.Label(frame, text="官方字体库（打开浏览器，检查许可后下载 TTF；不自动拉取）").pack(anchor="w")
    source = tk.StringVar(value=next(iter(SOURCES)))
    ttk.Combobox(frame, state="readonly", values=list(SOURCES), textvariable=source, width=48).pack(anchor="w", pady=4)
    def open_source():
        url = SOURCES[source.get()]
        if messagebox.askyesno("打开官方字体库", "将在浏览器打开：\n" + url):
            webbrowser.open(url)
    ttk.Button(frame, text="打开所选官方字体库", command=open_source).pack(anchor="w")
    def close():
        if busy:
            messagebox.showinfo("正在生成", "请等待生成完成后关闭，以免产生不完整资源包。")
        else:
            root.destroy()
    root.protocol("WM_DELETE_WINDOW", close)
    root.after(100, poll)
    root.mainloop()


if __name__ == "__main__":
    main()
