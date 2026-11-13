#include <pthread.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* Test-only side table. This is not a complete GNU pthread implementation.
 * Input is an AArch64 LP64 glibc mutex identity (48 bytes); the native mutex
 * lives separately. Only private normal/recursive/error-check kinds with a
 * null init attribute are supported. Never use this as a production adapter. */
typedef struct Entry {
    const void *identity;
    pthread_mutex_t mutex;
    unsigned users;
    unsigned acquisitions;
    int kind;
    struct Entry *next;
} Entry;
static pthread_mutex_t table_lock = PTHREAD_MUTEX_INITIALIZER;
static Entry *table;
typedef struct CondEntry {
    const void *identity;
    pthread_cond_t cond;
    unsigned users;
    struct CondEntry *next;
} CondEntry;
static CondEntry *conditions;

static int allocate_entry(const void *identity, int kind, Entry **out) {
    if (kind < 0 || kind > 2) return ENOTSUP;
    Entry *entry = (Entry *)calloc(1, sizeof(*entry));
    if (!entry) return ENOMEM;
    pthread_mutexattr_t attr;
    int rc = pthread_mutexattr_init(&attr);
    if (rc) { free(entry); return rc; }
    rc = pthread_mutexattr_settype(&attr, kind);
    if (!rc) rc = pthread_mutex_init(&entry->mutex, &attr);
    pthread_mutexattr_destroy(&attr);
    if (rc) { free(entry); return rc; }
    entry->identity = identity;
    entry->kind = kind;
    entry->next = table;
    table = entry;
    *out = entry;
    return 0;
}

static Entry *find_entry(const void *identity) {
    for (Entry *e = table; e; e = e->next) if (e->identity == identity) return e;
    return NULL;
}

static int acquire_entry(void *identity, int create, Entry **out) {
    if (!identity) return EINVAL;
    pthread_mutex_lock(&table_lock);
    Entry *e = find_entry(identity);
    int rc = 0;
    if (!e && create) {
        int32_t kind;
        memcpy(&kind, (const char *)identity + 16, sizeof(kind));
        rc = allocate_entry(identity, kind, &e);
    }
    if (!e && !rc) rc = EINVAL;
    if (!rc) { ++e->users; *out = e; }
    pthread_mutex_unlock(&table_lock);
    return rc;
}

static void release_entry(Entry *e, int acquire_delta, int release_delta) {
    pthread_mutex_lock(&table_lock);
    if (acquire_delta) ++e->acquisitions;
    if (release_delta && e->acquisitions) --e->acquisitions;
    --e->users;
    pthread_mutex_unlock(&table_lock);
}

int ut_gmutex_init(void *identity, const void *attr) {
    if (!identity) return EINVAL;
    if (attr) return ENOTSUP;
    pthread_mutex_lock(&table_lock);
    Entry *e = find_entry(identity);
    int rc = e ? EBUSY : allocate_entry(identity, 0, &e);
    pthread_mutex_unlock(&table_lock);
    return rc;
}
int ut_gmutex_lock(void *identity) {
    Entry *e; int rc = acquire_entry(identity, 1, &e);
    if (rc) return rc;
    rc = pthread_mutex_lock(&e->mutex);
    release_entry(e, rc == 0, 0);
    return rc;
}
int ut_gmutex_trylock(void *identity) {
    Entry *e; int rc = acquire_entry(identity, 1, &e);
    if (rc) return rc;
    rc = pthread_mutex_trylock(&e->mutex);
    release_entry(e, rc == 0, 0);
    return rc;
}
int ut_gmutex_unlock(void *identity) {
    Entry *e; int rc = acquire_entry(identity, 0, &e);
    if (rc) return rc;
    rc = pthread_mutex_unlock(&e->mutex);
    release_entry(e, 0, rc == 0);
    return rc;
}
int ut_gmutex_destroy(void *identity) {
    if (!identity) return EINVAL;
    pthread_mutex_lock(&table_lock);
    Entry **link = &table;
    while (*link && (*link)->identity != identity) link = &(*link)->next;
    Entry *e = *link;
    int rc = 0;
    if (e) {
        if (e->users || e->acquisitions) rc = EBUSY;
        else {
            rc = pthread_mutex_destroy(&e->mutex);
            if (!rc) { *link = e->next; free(e); }
        }
    }
    pthread_mutex_unlock(&table_lock);
    return rc;
}

/* Restricted default-clock, process-private condition variables. Opaque glibc
 * state must not be read/modified elsewhere while these adapters own it. */
static CondEntry *find_cond(const void *identity) {
    for (CondEntry *e = conditions; e; e = e->next) if (e->identity == identity) return e;
    return NULL;
}
static int allocate_cond(const void *identity, CondEntry **out) {
    CondEntry *entry = (CondEntry *)calloc(1, sizeof(*entry));
    if (!entry) return ENOMEM;
    int rc = pthread_cond_init(&entry->cond, NULL);
    if (rc) { free(entry); return rc; }
    entry->identity = identity; entry->next = conditions; conditions = entry; *out = entry;
    return 0;
}
static int acquire_cond(void *identity, int create, CondEntry **out) {
    if (!identity) return EINVAL;
    pthread_mutex_lock(&table_lock);
    CondEntry *entry = find_cond(identity); int rc = 0;
    if (!entry && create) {
        static const unsigned char zero[48] = {0};
        if (memcmp(identity, zero, sizeof(zero)) != 0) rc = ENOTSUP;
        else rc = allocate_cond(identity, &entry);
    }
    if (!entry && !rc) rc = EINVAL;
    if (!rc) { ++entry->users; *out = entry; }
    pthread_mutex_unlock(&table_lock);
    return rc;
}
static void release_cond(CondEntry *entry) {
    pthread_mutex_lock(&table_lock); --entry->users; pthread_mutex_unlock(&table_lock);
}
int ut_gcond_init(void *identity, const void *attr) {
    if (!identity) return EINVAL;
    if (attr) return ENOTSUP;
    pthread_mutex_lock(&table_lock);
    CondEntry *entry = find_cond(identity);
    int rc = entry ? EBUSY : allocate_cond(identity, &entry);
    pthread_mutex_unlock(&table_lock);
    return rc;
}
static int wait_cond(void *cond, void *mutex, const struct timespec *deadline) {
    CondEntry *c; Entry *m;
    int rc = acquire_cond(cond, 1, &c);
    if (rc) return rc;
    rc = acquire_entry(mutex, 0, &m);
    if (rc) { release_cond(c); return rc; }
    if (m->kind != 0) rc = ENOTSUP;
    else if (deadline) rc = pthread_cond_timedwait(&c->cond, &m->mutex, deadline);
    else rc = pthread_cond_wait(&c->cond, &m->mutex);
    release_entry(m, 0, 0); release_cond(c);
    return rc;
}
int ut_gcond_wait(void *cond, void *mutex) { return wait_cond(cond, mutex, NULL); }
int ut_gcond_timedwait(void *cond, void *mutex, const struct timespec *deadline) {
    if (!deadline) return EINVAL;
    return wait_cond(cond, mutex, deadline);
}
static int notify_cond(void *identity, int broadcast) {
    CondEntry *entry; int rc = acquire_cond(identity, 1, &entry);
    if (rc) return rc;
    rc = broadcast ? pthread_cond_broadcast(&entry->cond) : pthread_cond_signal(&entry->cond);
    release_cond(entry); return rc;
}
int ut_gcond_signal(void *identity) { return notify_cond(identity, 0); }
int ut_gcond_broadcast(void *identity) { return notify_cond(identity, 1); }
int ut_gcond_destroy(void *identity) {
    if (!identity) return EINVAL;
    pthread_mutex_lock(&table_lock);
    CondEntry **link = &conditions;
    while (*link && (*link)->identity != identity) link = &(*link)->next;
    CondEntry *entry = *link; int rc = 0;
    if (entry) {
        if (entry->users) rc = EBUSY;
        else {
            rc = pthread_cond_destroy(&entry->cond);
            if (!rc) { *link = entry->next; free(entry); }
        }
    }
    pthread_mutex_unlock(&table_lock);
    return rc;
}
