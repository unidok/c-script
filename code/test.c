void print(const char* s) { asm 25; asm 0; asm 200; }
void print(int s) { asm 25; asm 0; asm 201; }
void println() { print("\n"); }
void println(const char* s) { print(s); println(); }
void println(int s) { print(s); println(); }

void main() {
    int a = 10;
    println(a);
    println(malloc(40));
}

int malloc(int bytes) {
    println("такова нет");
}