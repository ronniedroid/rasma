#[derive(Debug, PartialEq)]
pub enum Token {
    LeftParen,
    RightParen,
    Symbol(String),
    Keyword(String),
    String(String),
}

pub fn lex(source: &str) -> Vec<Token> {
    let mut tokens = Vec::new();

    for character in source.chars() {
        match character {
            '(' => tokens.push(Token::LeftParen),
            ')' => tokens.push(Token::RightParen),
            _ => {}
        }
    }

    tokens
}
