// PixelTrigger unified multi-touch relay. Linux/Android NDK, root/privileged execution required.
#include <linux/input.h>
#include <linux/uinput.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/timerfd.h>
#include <sys/un.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <cerrno>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <optional>
#include <sstream>
#include <string>
#include <unordered_set>
#include <vector>

namespace {
volatile sig_atomic_t g_stop = 0;
void on_signal(int) { g_stop = 1; }

constexpr size_t kBitsPerLong = sizeof(unsigned long) * 8;
constexpr int kRecentTriggerCapacity = 256;

bool test_bit(const std::vector<unsigned long>& bits, int bit) {
    const size_t index = static_cast<size_t>(bit) / kBitsPerLong;
    const size_t offset = static_cast<size_t>(bit) % kBitsPerLong;
    return index < bits.size() && ((bits[index] >> offset) & 1UL) != 0;
}

std::vector<unsigned long> get_bits(int fd, int ev, int max_code) {
    std::vector<unsigned long> bits((static_cast<size_t>(max_code) + kBitsPerLong) / kBitsPerLong);
    if (ioctl(fd, EVIOCGBIT(ev, bits.size() * sizeof(unsigned long)), bits.data()) < 0) bits.clear();
    return bits;
}

bool emit_event(int fd, unsigned short type, unsigned short code, int value) {
    input_event ev{};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    return write(fd, &ev, sizeof(ev)) == sizeof(ev);
}

bool emit_sync(int fd) { return emit_event(fd, EV_SYN, SYN_REPORT, 0); }

struct DeviceInfo {
    input_id id{};
    std::string name;
    input_absinfo slot{};
    input_absinfo x{};
    input_absinfo y{};
    std::vector<unsigned long> ev_bits;
    std::vector<unsigned long> key_bits;
    std::vector<unsigned long> abs_bits;
};

std::optional<DeviceInfo> read_device_info(int fd) {
    DeviceInfo d;
    char name[UINPUT_MAX_NAME_SIZE]{};
    if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) return std::nullopt;
    d.name = name;
    if (ioctl(fd, EVIOCGID, &d.id) < 0) return std::nullopt;
    d.ev_bits = get_bits(fd, 0, EV_MAX);
    d.key_bits = get_bits(fd, EV_KEY, KEY_MAX);
    d.abs_bits = get_bits(fd, EV_ABS, ABS_MAX);
    if (!test_bit(d.ev_bits, EV_ABS) || !test_bit(d.abs_bits, ABS_MT_SLOT) ||
        !test_bit(d.abs_bits, ABS_MT_TRACKING_ID) || !test_bit(d.abs_bits, ABS_MT_POSITION_X) ||
        !test_bit(d.abs_bits, ABS_MT_POSITION_Y)) return std::nullopt;
    if (ioctl(fd, EVIOCGABS(ABS_MT_SLOT), &d.slot) < 0 ||
        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &d.x) < 0 ||
        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &d.y) < 0) return std::nullopt;
    return d;
}

bool setup_uinput_axis(int ufd, int physical_fd, int code, bool extra_slot) {
    input_absinfo abs{};
    if (ioctl(physical_fd, EVIOCGABS(code), &abs) < 0) return false;
    if (extra_slot && code == ABS_MT_SLOT) abs.maximum += 1;
    uinput_abs_setup setup{};
    setup.code = static_cast<__u16>(code);
    setup.absinfo = abs;
    return ioctl(ufd, UI_ABS_SETUP, &setup) == 0;
}

int create_virtual_touchscreen(int physical_fd, const DeviceInfo& d) {
    int ufd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (ufd < 0) return -1;
    for (int ev = 0; ev <= EV_MAX; ++ev) if (test_bit(d.ev_bits, ev)) ioctl(ufd, UI_SET_EVBIT, ev);
    if (test_bit(d.ev_bits, EV_KEY)) {
        for (int code = 0; code <= KEY_MAX; ++code) if (test_bit(d.key_bits, code)) ioctl(ufd, UI_SET_KEYBIT, code);
    }
    if (test_bit(d.ev_bits, EV_ABS)) {
        for (int code = 0; code <= ABS_MAX; ++code) {
            if (!test_bit(d.abs_bits, code)) continue;
            if (!setup_uinput_axis(ufd, physical_fd, code, code == ABS_MT_SLOT)) { close(ufd); return -1; }
        }
    }
    uinput_setup setup{};
    setup.id = d.id;
    std::snprintf(setup.name, sizeof(setup.name), "%s", d.name.c_str());
    if (ioctl(ufd, UI_DEV_SETUP, &setup) < 0 || ioctl(ufd, UI_DEV_CREATE) < 0) { close(ufd); return -1; }
    usleep(150000);
    return ufd;
}

struct TapCommand {
    long long id{}; float x{}; float y{}; int screen_w{}; int screen_h{}; int rotation{}; long long duration_us{};
};

std::optional<TapCommand> parse_tap(const std::string& line) {
    std::istringstream in(line);
    std::string op;
    TapCommand c;
    in >> op >> c.id >> c.x >> c.y >> c.screen_w >> c.screen_h >> c.rotation >> c.duration_us;
    if (!in || op != "TAP" || c.id <= 0 || c.screen_w <= 0 || c.screen_h <= 0 || c.duration_us <= 0) return std::nullopt;
    c.duration_us = std::clamp<long long>(c.duration_us, 500, 1000000);
    c.rotation = ((c.rotation % 4) + 4) % 4;
    return c;
}

struct RawPoint { int x; int y; };
RawPoint map_to_raw(const TapCommand& c, const DeviceInfo& d) {
    double lx = std::clamp<double>(c.x / std::max(1, c.screen_w - 1), 0.0, 1.0);
    double ly = std::clamp<double>(c.y / std::max(1, c.screen_h - 1), 0.0, 1.0);
    double nx = lx, ny = ly;
    switch (c.rotation) {
        case 1: nx = ly;       ny = 1.0 - lx; break;
        case 2: nx = 1.0-lx;   ny = 1.0 - ly; break;
        case 3: nx = 1.0-ly;   ny = lx; break;
        default: break;
    }
    const int rx = d.x.minimum + static_cast<int>(std::llround(nx * (d.x.maximum - d.x.minimum)));
    const int ry = d.y.minimum + static_cast<int>(std::llround(ny * (d.y.maximum - d.y.minimum)));
    return {std::clamp(rx, d.x.minimum, d.x.maximum), std::clamp(ry, d.y.minimum, d.y.maximum)};
}

class RecentIds {
public:
    bool accept(long long id) {
        if (set_.count(id)) return false;
        set_.insert(id); order_.push_back(id);
        while (order_.size() > kRecentTriggerCapacity) { set_.erase(order_.front()); order_.pop_front(); }
        return true;
    }
private:
    std::unordered_set<long long> set_;
    std::deque<long long> order_;
};

int make_server(const std::string& path) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    sockaddr_un addr{}; addr.sun_family = AF_UNIX;
    if (path.size() >= sizeof(addr.sun_path)) { close(fd); return -1; }
    std::strncpy(addr.sun_path, path.c_str(), sizeof(addr.sun_path) - 1);
    unlink(path.c_str());
    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0 || listen(fd, 4) < 0) { close(fd); return -1; }
    chmod(path.c_str(), 0600);
    return fd;
}

void respond(int fd, const std::string& s) { std::string line = s + "\n"; (void)write(fd, line.data(), line.size()); }
} // namespace

int main(int argc, char** argv) {
    std::string device, socket_path;
    for (int i = 1; i < argc; ++i) {
        if (std::string(argv[i]) == "--device" && i + 1 < argc) device = argv[++i];
        else if (std::string(argv[i]) == "--socket" && i + 1 < argc) socket_path = argv[++i];
    }
    if (device.empty() || socket_path.empty()) {
        std::fprintf(stderr, "usage: %s --device /dev/input/eventX --socket /path/touchd.sock\n", argv[0]);
        return 2;
    }
    signal(SIGINT, on_signal); signal(SIGTERM, on_signal);
    int pfd = open(device.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (pfd < 0) { perror("open physical"); return 3; }
    auto info_opt = read_device_info(pfd);
    if (!info_opt) { std::fprintf(stderr, "not a Type-B multi-touch touchscreen\n"); close(pfd); return 4; }
    DeviceInfo info = *info_opt;
    int ufd = create_virtual_touchscreen(pfd, info);
    if (ufd < 0) { perror("uinput"); close(pfd); return 5; }
    const int synth_slot = info.slot.maximum + 1;
    int server = make_server(socket_path);
    if (server < 0) { perror("socket"); ioctl(ufd, UI_DEV_DESTROY); close(ufd); close(pfd); return 6; }
    if (ioctl(pfd, EVIOCGRAB, 1) < 0) {
        perror("EVIOCGRAB"); close(server); unlink(socket_path.c_str()); ioctl(ufd, UI_DEV_DESTROY); close(ufd); close(pfd); return 7;
    }

    int tfd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK | TFD_CLOEXEC);
    int ep = epoll_create1(EPOLL_CLOEXEC);
    auto add = [&](int fd) { epoll_event e{}; e.events = EPOLLIN; e.data.fd = fd; return epoll_ctl(ep, EPOLL_CTL_ADD, fd, &e); };
    add(pfd); add(server); add(tfd);

    std::vector<bool> physical_active(static_cast<size_t>(std::max(0, info.slot.maximum) + 1), false);
    int current_slot = 0, physical_contacts = 0, client = -1;
    std::string client_buffer;
    RecentIds recent_ids;
    std::optional<TapCommand> active_tap;
    long long synthetic_tracking_id = 1000000000LL;

    auto synthetic_down = [&](const TapCommand& c) {
        RawPoint p = map_to_raw(c, info);
        emit_event(ufd, EV_ABS, ABS_MT_SLOT, synth_slot);
        emit_event(ufd, EV_ABS, ABS_MT_TRACKING_ID, static_cast<int>(synthetic_tracking_id++ & 0x7fffffff));
        emit_event(ufd, EV_ABS, ABS_MT_POSITION_X, p.x);
        emit_event(ufd, EV_ABS, ABS_MT_POSITION_Y, p.y);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOUCH)) emit_event(ufd, EV_KEY, BTN_TOUCH, 1);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOOL_FINGER)) emit_event(ufd, EV_KEY, BTN_TOOL_FINGER, 1);
        emit_sync(ufd);
        itimerspec timer{};
        timer.it_value.tv_sec = c.duration_us / 1000000;
        timer.it_value.tv_nsec = (c.duration_us % 1000000) * 1000;
        timerfd_settime(tfd, 0, &timer, nullptr);
    };
    auto synthetic_up = [&]() {
        emit_event(ufd, EV_ABS, ABS_MT_SLOT, synth_slot);
        emit_event(ufd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOUCH)) emit_event(ufd, EV_KEY, BTN_TOUCH, 0);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOOL_FINGER)) emit_event(ufd, EV_KEY, BTN_TOOL_FINGER, 0);
        emit_sync(ufd);
    };

    std::array<epoll_event, 8> events{};
    while (!g_stop) {
        int n = epoll_wait(ep, events.data(), static_cast<int>(events.size()), 100);
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) break;
        for (int i = 0; i < n; ++i) {
            const int fd = events[i].data.fd;
            if (fd == pfd) {
                input_event batch[64]; ssize_t bytes;
                while ((bytes = read(pfd, batch, sizeof(batch))) > 0) {
                    size_t count = static_cast<size_t>(bytes) / sizeof(input_event);
                    for (size_t j = 0; j < count; ++j) {
                        const input_event& ev = batch[j];
                        if (ev.type == EV_ABS && ev.code == ABS_MT_SLOT) current_slot = ev.value;
                        if (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID && current_slot >= 0 && current_slot < static_cast<int>(physical_active.size())) {
                            bool was = physical_active[current_slot], now = ev.value >= 0;
                            if (was != now) physical_contacts += now ? 1 : -1;
                            physical_active[current_slot] = now;
                        }
                        (void)write(ufd, &ev, sizeof(ev));
                    }
                }
            } else if (fd == server) {
                int cfd = accept4(server, nullptr, nullptr, SOCK_CLOEXEC | SOCK_NONBLOCK);
                if (cfd >= 0) {
                    if (client >= 0) { epoll_ctl(ep, EPOLL_CTL_DEL, client, nullptr); close(client); }
                    client = cfd; client_buffer.clear(); add(client);
                }
            } else if (fd == tfd) {
                uint64_t expirations{}; (void)read(tfd, &expirations, sizeof(expirations));
                if (active_tap) {
                    const long long id = active_tap->id;
                    synthetic_up();
                    if (client >= 0) respond(client, "DONE " + std::to_string(id));
                    active_tap.reset();
                }
            } else if (fd == client) {
                char buf[512]; ssize_t r = read(client, buf, sizeof(buf));
                if (r <= 0) {
                    epoll_ctl(ep, EPOLL_CTL_DEL, client, nullptr); close(client); client = -1; client_buffer.clear(); continue;
                }
                client_buffer.append(buf, static_cast<size_t>(r));
                size_t nl;
                while ((nl = client_buffer.find('\n')) != std::string::npos) {
                    std::string line = client_buffer.substr(0, nl); client_buffer.erase(0, nl + 1);
                    auto cmd = parse_tap(line);
                    if (!cmd) { respond(client, "ERR malformed"); continue; }
                    if (!recent_ids.accept(cmd->id)) { respond(client, "DUP " + std::to_string(cmd->id)); continue; }
                    if (active_tap) { respond(client, "ERR busy"); continue; }
                    active_tap = *cmd;
                    respond(client, "ACK " + std::to_string(cmd->id));
                    synthetic_down(*cmd);
                }
            }
        }
    }

    if (active_tap) synthetic_up();
    if (client >= 0) close(client);
    ioctl(pfd, EVIOCGRAB, 0);
    close(tfd); close(server); unlink(socket_path.c_str());
    ioctl(ufd, UI_DEV_DESTROY); close(ufd); close(pfd); close(ep);
    return 0;
}
