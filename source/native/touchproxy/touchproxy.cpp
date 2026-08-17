// PixelTrigger unified multi-touch relay. Linux/Android NDK, root/privileged execution required.
#include <linux/input.h>
#include <linux/uinput.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/timerfd.h>
#include <sys/un.h>
#include <dirent.h>
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
constexpr int kExitStreamDropped = 20;

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

std::vector<unsigned long> get_prop_bits(int fd) {
    std::vector<unsigned long> bits((static_cast<size_t>(INPUT_PROP_MAX) + kBitsPerLong) / kBitsPerLong);
    if (ioctl(fd, EVIOCGPROP(bits.size() * sizeof(unsigned long)), bits.data()) < 0) bits.clear();
    return bits;
}

bool write_all(int fd, const void* data, size_t size) {
    const auto* p = static_cast<const unsigned char*>(data);
    size_t written = 0;
    while (written < size) {
        const ssize_t n = write(fd, p + written, size - written);
        if (n > 0) { written += static_cast<size_t>(n); continue; }
        if (n < 0 && errno == EINTR) continue;
        return false;
    }
    return true;
}

bool emit_event(int fd, unsigned short type, unsigned short code, int value) {
    input_event ev{};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    return write_all(fd, &ev, sizeof(ev));
}

bool emit_sync(int fd) { return emit_event(fd, EV_SYN, SYN_REPORT, 0); }

struct DeviceInfo {
    input_id id{};
    std::string name;
    std::string path;
    input_absinfo slot{};
    input_absinfo x{};
    input_absinfo y{};
    std::vector<unsigned long> ev_bits;
    std::vector<unsigned long> key_bits;
    std::vector<unsigned long> abs_bits;
    std::vector<unsigned long> prop_bits;

    bool direct() const { return test_bit(prop_bits, INPUT_PROP_DIRECT); }
    int slots() const { return slot.maximum - slot.minimum + 1; }
    long long area() const {
        return static_cast<long long>(x.maximum - x.minimum + 1) *
               static_cast<long long>(y.maximum - y.minimum + 1);
    }
};

std::optional<DeviceInfo> read_device_info(int fd, const std::string& path = {}) {
    DeviceInfo d;
    char name[UINPUT_MAX_NAME_SIZE]{};
    if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) return std::nullopt;
    d.name = name;
    d.path = path;
    if (ioctl(fd, EVIOCGID, &d.id) < 0) return std::nullopt;
    d.ev_bits = get_bits(fd, 0, EV_MAX);
    d.key_bits = get_bits(fd, EV_KEY, KEY_MAX);
    d.abs_bits = get_bits(fd, EV_ABS, ABS_MAX);
    d.prop_bits = get_prop_bits(fd);
    if (!test_bit(d.ev_bits, EV_ABS) ||
        !test_bit(d.abs_bits, ABS_MT_SLOT) ||
        !test_bit(d.abs_bits, ABS_MT_TRACKING_ID) ||
        !test_bit(d.abs_bits, ABS_MT_POSITION_X) ||
        !test_bit(d.abs_bits, ABS_MT_POSITION_Y)) {
        return std::nullopt;
    }
    if (ioctl(fd, EVIOCGABS(ABS_MT_SLOT), &d.slot) < 0 ||
        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &d.x) < 0 ||
        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &d.y) < 0) {
        return std::nullopt;
    }
    if (d.slot.maximum < d.slot.minimum || d.x.maximum <= d.x.minimum || d.y.maximum <= d.y.minimum) {
        return std::nullopt;
    }
    return d;
}

std::vector<DeviceInfo> probe_touchscreens() {
    std::vector<DeviceInfo> result;
    DIR* dir = opendir("/dev/input");
    if (!dir) return result;
    while (dirent* ent = readdir(dir)) {
        if (std::strncmp(ent->d_name, "event", 5) != 0) continue;
        const std::string path = std::string("/dev/input/") + ent->d_name;
        const int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        auto info = read_device_info(fd, path);
        close(fd);
        if (info) result.push_back(std::move(*info));
    }
    closedir(dir);
    std::sort(result.begin(), result.end(), [](const DeviceInfo& a, const DeviceInfo& b) {
        if (a.direct() != b.direct()) return a.direct() > b.direct();
        if (a.area() != b.area()) return a.area() > b.area();
        return a.slots() > b.slots();
    });
    return result;
}

void print_probe(const std::vector<DeviceInfo>& devices) {
    for (const DeviceInfo& d : devices) {
        std::printf("PROBE\t%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%s\n",
                    d.path.c_str(), d.direct() ? 1 : 0, d.slots(),
                    d.x.minimum, d.x.maximum, d.y.minimum, d.y.maximum,
                    d.id.vendor, d.name.c_str());
    }
    std::fflush(stdout);
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

    for (int ev = 0; ev <= EV_MAX; ++ev) {
        if (test_bit(d.ev_bits, ev) && ioctl(ufd, UI_SET_EVBIT, ev) < 0) {
            close(ufd); return -1;
        }
    }
    for (int prop = 0; prop <= INPUT_PROP_MAX; ++prop) {
        if (test_bit(d.prop_bits, prop) && ioctl(ufd, UI_SET_PROPBIT, prop) < 0) {
            close(ufd); return -1;
        }
    }
    if (test_bit(d.ev_bits, EV_KEY)) {
        for (int code = 0; code <= KEY_MAX; ++code) {
            if (test_bit(d.key_bits, code) && ioctl(ufd, UI_SET_KEYBIT, code) < 0) {
                close(ufd); return -1;
            }
        }
    }
    if (test_bit(d.ev_bits, EV_ABS)) {
        for (int code = 0; code <= ABS_MAX; ++code) {
            if (!test_bit(d.abs_bits, code)) continue;
            if (!setup_uinput_axis(ufd, physical_fd, code, code == ABS_MT_SLOT)) {
                close(ufd); return -1;
            }
        }
    }

    uinput_setup setup{};
    setup.id = d.id;
    std::snprintf(setup.name, sizeof(setup.name), "%s", d.name.c_str());
    if (ioctl(ufd, UI_DEV_SETUP, &setup) < 0 || ioctl(ufd, UI_DEV_CREATE) < 0) {
        close(ufd); return -1;
    }
    usleep(180000);
    return ufd;
}

struct TapCommand {
    long long id{};
    float x{};
    float y{};
    int screen_w{};
    int screen_h{};
    int rotation{};
    long long duration_us{};
};

std::optional<TapCommand> parse_tap(const std::string& line) {
    std::istringstream in(line);
    std::string op;
    TapCommand c;
    in >> op >> c.id >> c.x >> c.y >> c.screen_w >> c.screen_h >> c.rotation >> c.duration_us;
    if (!in || op != "TAP" || c.id <= 0 || c.screen_w <= 0 || c.screen_h <= 0 || c.duration_us <= 0) {
        return std::nullopt;
    }
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
        set_.insert(id);
        order_.push_back(id);
        while (order_.size() > kRecentTriggerCapacity) {
            set_.erase(order_.front());
            order_.pop_front();
        }
        return true;
    }
private:
    std::unordered_set<long long> set_;
    std::deque<long long> order_;
};

struct Endpoint {
    int input_fd{-1};
    int output_fd{-1};
    int server_fd{-1};
    int client_fd{-1};
    std::string socket_path;
    bool stdio_mode{false};
};

int make_server(const std::string& path, uid_t owner_uid, gid_t owner_gid) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    if (path.size() >= sizeof(addr.sun_path)) { close(fd); return -1; }
    std::strncpy(addr.sun_path, path.c_str(), sizeof(addr.sun_path) - 1);
    unlink(path.c_str());
    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0 || listen(fd, 4) < 0) {
        close(fd); return -1;
    }
    if (owner_uid != static_cast<uid_t>(-1) || owner_gid != static_cast<gid_t>(-1)) {
        const uid_t uid = owner_uid == static_cast<uid_t>(-1) ? 0 : owner_uid;
        const gid_t gid = owner_gid == static_cast<gid_t>(-1) ? 0 : owner_gid;
        if (chown(path.c_str(), uid, gid) < 0) { close(fd); unlink(path.c_str()); return -1; }
    }
    if (chmod(path.c_str(), 0600) < 0) { close(fd); unlink(path.c_str()); return -1; }
    return fd;
}

void respond_fd(int fd, const std::string& s) {
    if (fd < 0) return;
    const std::string line = s + "\n";
    (void)write_all(fd, line.data(), line.size());
}

void close_client(Endpoint& endpoint, int epoll_fd) {
    if (endpoint.stdio_mode) return;
    if (endpoint.client_fd >= 0) {
        epoll_ctl(epoll_fd, EPOLL_CTL_DEL, endpoint.client_fd, nullptr);
        close(endpoint.client_fd);
        endpoint.client_fd = -1;
        endpoint.input_fd = -1;
        endpoint.output_fd = -1;
    }
}

} // namespace

int main(int argc, char** argv) {
    std::string device;
    std::string socket_path;
    bool auto_device = false;
    bool probe_only = false;
    bool stdio_mode = false;
    uid_t socket_uid = static_cast<uid_t>(-1);
    gid_t socket_gid = static_cast<gid_t>(-1);

    for (int i = 1; i < argc; ++i) {
        const std::string arg = argv[i];
        if (arg == "--device" && i + 1 < argc) device = argv[++i];
        else if (arg == "--socket" && i + 1 < argc) socket_path = argv[++i];
        else if (arg == "--socket-uid" && i + 1 < argc) socket_uid = static_cast<uid_t>(std::strtoul(argv[++i], nullptr, 10));
        else if (arg == "--socket-gid" && i + 1 < argc) socket_gid = static_cast<gid_t>(std::strtoul(argv[++i], nullptr, 10));
        else if (arg == "--auto") auto_device = true;
        else if (arg == "--probe") probe_only = true;
        else if (arg == "--stdio") stdio_mode = true;
    }

    if (probe_only) {
        const auto devices = probe_touchscreens();
        print_probe(devices);
        return devices.empty() ? 8 : 0;
    }

    if (device.empty() && auto_device) {
        const auto devices = probe_touchscreens();
        if (!devices.empty()) device = devices.front().path;
    }
    if (device.empty() || (!stdio_mode && socket_path.empty())) {
        std::fprintf(stderr,
                     "usage: %s (--device /dev/input/eventX|--auto) (--stdio|--socket PATH [--socket-uid UID --socket-gid GID])\n"
                     "       %s --probe\n",
                     argv[0], argv[0]);
        return 2;
    }

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
    signal(SIGHUP, on_signal);

    int pfd = open(device.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (pfd < 0) { perror("open physical"); return 3; }
    auto info_opt = read_device_info(pfd, device);
    if (!info_opt) {
        std::fprintf(stderr, "not a Type-B multi-touch touchscreen: %s\n", device.c_str());
        close(pfd);
        return 4;
    }
    DeviceInfo info = *info_opt;

    int ufd = create_virtual_touchscreen(pfd, info);
    if (ufd < 0) { perror("uinput"); close(pfd); return 5; }
    const int synth_slot = info.slot.maximum + 1;

    Endpoint endpoint;
    endpoint.stdio_mode = stdio_mode;
    if (stdio_mode) {
        endpoint.input_fd = STDIN_FILENO;
        endpoint.output_fd = STDOUT_FILENO;
    } else {
        endpoint.socket_path = socket_path;
        endpoint.server_fd = make_server(socket_path, socket_uid, socket_gid);
        if (endpoint.server_fd < 0) {
            perror("socket");
            ioctl(ufd, UI_DEV_DESTROY); close(ufd); close(pfd);
            return 6;
        }
    }

    if (ioctl(pfd, EVIOCGRAB, 1) < 0) {
        perror("EVIOCGRAB");
        if (endpoint.server_fd >= 0) close(endpoint.server_fd);
        if (!socket_path.empty()) unlink(socket_path.c_str());
        ioctl(ufd, UI_DEV_DESTROY); close(ufd); close(pfd);
        return 7;
    }

    int tfd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK | TFD_CLOEXEC);
    int ep = epoll_create1(EPOLL_CLOEXEC);
    if (tfd < 0 || ep < 0) {
        perror("epoll/timerfd");
        ioctl(pfd, EVIOCGRAB, 0);
        if (tfd >= 0) close(tfd);
        if (ep >= 0) close(ep);
        if (endpoint.server_fd >= 0) close(endpoint.server_fd);
        if (!socket_path.empty()) unlink(socket_path.c_str());
        ioctl(ufd, UI_DEV_DESTROY); close(ufd); close(pfd);
        return 9;
    }

    auto add = [&](int fd) {
        epoll_event e{};
        e.events = EPOLLIN | EPOLLHUP | EPOLLERR;
        e.data.fd = fd;
        return epoll_ctl(ep, EPOLL_CTL_ADD, fd, &e);
    };
    add(pfd);
    add(tfd);
    if (stdio_mode) add(STDIN_FILENO); else add(endpoint.server_fd);

    std::vector<bool> physical_active(static_cast<size_t>(std::max(0, info.slot.maximum) + 1), false);
    int current_slot = std::max(0, info.slot.minimum);
    int physical_contacts = 0;
    std::string command_buffer;
    RecentIds recent_ids;
    std::optional<TapCommand> active_tap;
    long long synthetic_tracking_id = 1000000000LL;
    bool stream_dropped = false;

    auto restore_physical_slot = [&]() {
        emit_event(ufd, EV_ABS, ABS_MT_SLOT, current_slot);
    };

    auto optional_axis_value = [&](int code, int divisor) -> std::optional<int> {
        if (!test_bit(info.abs_bits, code)) return std::nullopt;
        input_absinfo a{};
        if (ioctl(pfd, EVIOCGABS(code), &a) < 0) return std::nullopt;
        const int span = std::max(0, a.maximum - a.minimum);
        return std::clamp(a.minimum + std::max(1, span / divisor), a.minimum, a.maximum);
    };

    auto synthetic_down = [&](const TapCommand& c) {
        RawPoint p = map_to_raw(c, info);
        emit_event(ufd, EV_ABS, ABS_MT_SLOT, synth_slot);
        emit_event(ufd, EV_ABS, ABS_MT_TRACKING_ID,
                   static_cast<int>(synthetic_tracking_id++ & 0x7fffffff));
        emit_event(ufd, EV_ABS, ABS_MT_POSITION_X, p.x);
        emit_event(ufd, EV_ABS, ABS_MT_POSITION_Y, p.y);
        if (auto v = optional_axis_value(ABS_MT_PRESSURE, 2)) emit_event(ufd, EV_ABS, ABS_MT_PRESSURE, *v);
        if (auto v = optional_axis_value(ABS_MT_TOUCH_MAJOR, 4)) emit_event(ufd, EV_ABS, ABS_MT_TOUCH_MAJOR, *v);
        if (test_bit(info.abs_bits, ABS_MT_TOOL_TYPE)) emit_event(ufd, EV_ABS, ABS_MT_TOOL_TYPE, MT_TOOL_FINGER);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOUCH)) emit_event(ufd, EV_KEY, BTN_TOUCH, 1);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOOL_FINGER)) emit_event(ufd, EV_KEY, BTN_TOOL_FINGER, 1);
        restore_physical_slot();
        emit_sync(ufd);

        itimerspec timer{};
        timer.it_value.tv_sec = c.duration_us / 1000000;
        timer.it_value.tv_nsec = (c.duration_us % 1000000) * 1000;
        timerfd_settime(tfd, 0, &timer, nullptr);
    };

    auto synthetic_up = [&]() {
        emit_event(ufd, EV_ABS, ABS_MT_SLOT, synth_slot);
        emit_event(ufd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        if (test_bit(info.abs_bits, ABS_MT_PRESSURE)) emit_event(ufd, EV_ABS, ABS_MT_PRESSURE, 0);
        if (test_bit(info.abs_bits, ABS_MT_TOUCH_MAJOR)) emit_event(ufd, EV_ABS, ABS_MT_TOUCH_MAJOR, 0);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOUCH)) emit_event(ufd, EV_KEY, BTN_TOUCH, 0);
        if (physical_contacts == 0 && test_bit(info.key_bits, BTN_TOOL_FINGER)) emit_event(ufd, EV_KEY, BTN_TOOL_FINGER, 0);
        restore_physical_slot();
        emit_sync(ufd);
    };

    auto respond = [&](const std::string& text) {
        respond_fd(endpoint.output_fd, text);
    };

    auto process_command = [&](const std::string& line) {
        if (line == "PING") { respond("PONG"); return; }
        if (line == "STOP") { respond("BYE"); g_stop = 1; return; }
        auto cmd = parse_tap(line);
        if (!cmd) { respond("ERR malformed"); return; }
        if (!recent_ids.accept(cmd->id)) { respond("DUP " + std::to_string(cmd->id)); return; }
        if (active_tap) { respond("ERR busy"); return; }
        active_tap = *cmd;
        respond("ACK " + std::to_string(cmd->id));
        synthetic_down(*cmd);
    };

    respond_fd(endpoint.output_fd,
               "READY " + device + " " + std::to_string(info.slots()) + " " +
               std::to_string(info.x.minimum) + " " + std::to_string(info.x.maximum) + " " +
               std::to_string(info.y.minimum) + " " + std::to_string(info.y.maximum));

    std::array<epoll_event, 8> events{};
    while (!g_stop) {
        int n = epoll_wait(ep, events.data(), static_cast<int>(events.size()), 100);
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) break;

        for (int i = 0; i < n; ++i) {
            const int fd = events[i].data.fd;
            const uint32_t flags = events[i].events;

            if (fd == pfd) {
                input_event batch[64];
                ssize_t bytes;
                while ((bytes = read(pfd, batch, sizeof(batch))) > 0) {
                    const size_t count = static_cast<size_t>(bytes) / sizeof(input_event);
                    for (size_t j = 0; j < count; ++j) {
                        input_event ev = batch[j];
                        if (ev.type == EV_SYN && ev.code == SYN_DROPPED) {
                            stream_dropped = true;
                            g_stop = 1;
                            break;
                        }
                        if (ev.type == EV_ABS && ev.code == ABS_MT_SLOT) current_slot = ev.value;
                        if (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID &&
                            current_slot >= 0 && current_slot < static_cast<int>(physical_active.size())) {
                            const bool was = physical_active[current_slot];
                            const bool now = ev.value >= 0;
                            if (was != now) physical_contacts += now ? 1 : -1;
                            physical_active[current_slot] = now;
                        }
                        if (active_tap && ev.type == EV_KEY && ev.value == 0 &&
                            (ev.code == BTN_TOUCH || ev.code == BTN_TOOL_FINGER)) {
                            ev.value = 1;
                        }
                        (void)write_all(ufd, &ev, sizeof(ev));
                    }
                    if (g_stop) break;
                }
            } else if (!stdio_mode && fd == endpoint.server_fd) {
                const int cfd = accept4(endpoint.server_fd, nullptr, nullptr, SOCK_CLOEXEC | SOCK_NONBLOCK);
                if (cfd >= 0) {
                    close_client(endpoint, ep);
                    endpoint.client_fd = cfd;
                    endpoint.input_fd = cfd;
                    endpoint.output_fd = cfd;
                    command_buffer.clear();
                    add(cfd);
                }
            } else if (fd == tfd) {
                uint64_t expirations{};
                (void)read(tfd, &expirations, sizeof(expirations));
                if (active_tap) {
                    const long long id = active_tap->id;
                    synthetic_up();
                    respond("DONE " + std::to_string(id));
                    active_tap.reset();
                }
            } else if (fd == endpoint.input_fd || (stdio_mode && fd == STDIN_FILENO)) {
                if ((flags & (EPOLLHUP | EPOLLERR)) != 0 && (flags & EPOLLIN) == 0) {
                    if (stdio_mode) g_stop = 1; else close_client(endpoint, ep);
                    continue;
                }
                char buf[512];
                const ssize_t r = read(fd, buf, sizeof(buf));
                if (r <= 0) {
                    if (stdio_mode) g_stop = 1; else close_client(endpoint, ep);
                    continue;
                }
                command_buffer.append(buf, static_cast<size_t>(r));
                size_t nl;
                while ((nl = command_buffer.find('\n')) != std::string::npos) {
                    const std::string line = command_buffer.substr(0, nl);
                    command_buffer.erase(0, nl + 1);
                    process_command(line);
                    if (g_stop) break;
                }
            }
        }
    }

    if (active_tap) synthetic_up();
    close_client(endpoint, ep);
    ioctl(pfd, EVIOCGRAB, 0);
    close(tfd);
    if (endpoint.server_fd >= 0) close(endpoint.server_fd);
    if (!endpoint.socket_path.empty()) unlink(endpoint.socket_path.c_str());
    ioctl(ufd, UI_DEV_DESTROY);
    close(ufd);
    close(pfd);
    close(ep);
    return stream_dropped ? kExitStreamDropped : 0;
}
