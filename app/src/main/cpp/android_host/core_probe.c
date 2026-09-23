#include "compiler.h"
#include "pccore.h"
#include "cpucore.h"

int kairo98_core_probe(UINT16 *code_segment, UINT16 *instruction_pointer) {
    pccore_init();
    pccore_reset();
    *code_segment = CPU_CS;
    *instruction_pointer = CPU_IP;
    pccore_term();
    return (*code_segment == 0xf000 && *instruction_pointer == 0xfff0) ? 0 : 1;
}
