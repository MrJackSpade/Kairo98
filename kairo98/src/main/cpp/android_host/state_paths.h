#pragma once

#include "compiler.h"

/*
 * Where a disk a save state names is now. A working copy that moved after the state was saved
 * (its game's key changed, Kairo#56) is listed in the slot's moved.txt as "old\tnew"; the core
 * reopens each disk it restores by its recorded path, so that path goes through this first.
 */
const OEMCHAR *kairo98_state_path(const OEMCHAR *recorded);
