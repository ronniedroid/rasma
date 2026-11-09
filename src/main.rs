mod lexer;

fn main() {
    let source = "(())";
    let tokens = lexer::lex(source);

    println!("{:?}", tokens);
}
