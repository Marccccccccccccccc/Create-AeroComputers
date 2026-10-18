package net.tui.aerocomputers.lang;

/**
 * A lexed piece of source text. start/end are character offsets into the string
 * that was lexed (end is exclusive).
 */
public record Token(TokenType type, String text, int start, int end) {}