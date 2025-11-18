def to_markdown(text: str) -> str:
    lines = text.splitlines()
    markdown_lines = []
    first_non_empty = True

    for raw_line in lines:
        line = raw_line.strip()

        # Linha em branco -> quebra de parágrafo
        if not line:
            markdown_lines.append("")
            continue

        # Primeira linha não vazia -> título principal
        if first_non_empty:
            markdown_lines.append(f"# {line}")
            first_non_empty = False
            continue

        # Linhas TODAS MAIÚSCULAS -> subtítulo
        if line.isupper() and len(line) > 3:
            markdown_lines.append(f"## {line.title()}")
            continue

        # Linhas que já parecem lista -> mantém como lista
        if line.startswith("- ") or line.startswith("* "):
            markdown_lines.append(line)
            continue

        # Linhas que começam com número + ponto -> lista numerada
        if line[0].isdigit() and line[1:3] in [") ", ". "]:
            markdown_lines.append(f"1. {line[3:]}")
            continue

        # Caso padrão -> parágrafo
        markdown_lines.append(line)

    return "\n".join(markdown_lines)


if __name__ == "__main__":
    print("Cole seu texto (finalize com uma linha vazia + Ctrl+D / Ctrl+Z):\n")
    import sys

    # Lê tudo da entrada padrão
    input_text = sys.stdin.read()

    md = to_markdown(input_text)
    print("\n--- TEXTO EM MARKDOWN ---\n")
    print(md)
