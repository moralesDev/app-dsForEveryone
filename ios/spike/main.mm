/*
 * DsSpike: la prueba de la fase iOS-0 (docs/ios.md).
 *
 * Una sola pregunta: ¿el intérprete de melonDS, sin JIT, da 60 fotogramas
 * por segundo en un iPhone/iPad? Para responderla:
 *
 * - «Elegir ROM» abre el selector de Archivos; la ROM se carga con la BIOS y
 *   el firmware libres que trae melonDS (FreeBIOS), igual que en Android.
 * - Un hilo propio corre el core. En «Sin límite» corre tan rápido como
 *   puede: los fotogramas por segundo que salgan son la capacidad real del
 *   intérprete en este aparato. En «60 Hz» va al ritmo de la DS
 *   (16 715 113 ns por fotograma), como en la app de verdad.
 * - Arriba se muestran tres cifras: fotogramas por segundo del core, cuánto
 *   tarda de media un fotograma (16,7 ms es el presupuesto) y el porcentaje
 *   de velocidad respecto a una DS real.
 * - Tocar la pantalla de abajo es el lápiz; la fila de botones basta para
 *   pasar menús y llegar a jugar.
 *
 * Sin audio, sin partidas, sin nada más: es código de usar y tirar. Lo que
 * no es de usar y tirar es lo de debajo: el core se maneja sólo por
 * ds_core.h, la misma frontera que usa Android (fase iOS-1).
 */
#import <UIKit/UIKit.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>
#include <mach/mach.h>
#include <mach/mach_time.h>

#include <atomic>

#include "ds_core.h"

// ---------------------------------------------------------------------------
// El core, en su hilo
// ---------------------------------------------------------------------------

static constexpr uint64_t kFrameNanos = 16715113ull;  // 59,8261 Hz, ver CLAUDE.md.
static constexpr int kWidth = DS_SCREEN_WIDTH;
static constexpr int kHeight = DS_SCREEN_HEIGHT * 2;

@interface DsCore : NSObject
@property(atomic) BOOL unlimited;
@property(nonatomic, copy) void (^onFrame)(NSData* pixels);
- (NSString*)loadROM:(NSData*)rom;
- (void)setButton:(int)bit pressed:(BOOL)pressed;
- (void)touchAtX:(int)x y:(int)y down:(BOOL)down;
- (void)takeStats:(double*)fps msPerFrame:(double*)ms;
@end

@implementation DsCore {
    DsEmulator* _emu;
    NSThread* _thread;
    std::atomic<bool> _running;
    std::atomic<uint32_t> _keys;      // 1 = pulsado, orden de DsButton.
    std::atomic<uint32_t> _touch;     // bit 31 = abajo, x en 8..15, y en 0..7.
    std::atomic<bool> _framePending;  // el hilo principal aún no pintó el último.
    std::atomic<uint64_t> _frames;
    std::atomic<uint64_t> _coreNanos;
    mach_timebase_info_data_t _timebase;
}

- (instancetype)init {
    if ((self = [super init])) {
        _running = false;
        _keys = 0;
        _touch = 0;
        _framePending = false;
        _frames = 0;
        _coreNanos = 0;
        mach_timebase_info(&_timebase);
        _emu = ds_create();
        // Sin audio en la prueba: silenciado, ds_run_frame vacía el SPU sin
        // llenar el anillo.
        ds_set_audio_muted(_emu, true);
    }
    return self;
}

- (uint64_t)nanosNow {
    return mach_absolute_time() * _timebase.numer / _timebase.denom;
}

- (NSString*)loadROM:(NSData*)rom {
    [self stop];

    // Con ds_core: FreeBIOS y firmware generado, como en Android. Sin JIT,
    // que en esta compilación ni siquiera existe (ENABLE_JIT=OFF).
    int result = ds_load(_emu, static_cast<const uint8_t*>(rom.bytes),
                         static_cast<uint32_t>(rom.length), nullptr, 0);
    if (result == DS_LOAD_INVALID_ROM) return @"melonDS no reconoce el archivo como ROM de DS.";
    if (result != DS_LOAD_OK) return @"No se pudo cargar la ROM.";

    _frames = 0;
    _coreNanos = 0;
    _running = true;
    _thread = [[NSThread alloc] initWithTarget:self selector:@selector(loop) object:nil];
    _thread.qualityOfService = NSQualityOfServiceUserInteractive;
    _thread.name = @"emulacion";
    [_thread start];
    return nil;
}

- (void)stop {
    if (!_running) return;
    _running = false;
    while (!_thread.finished) usleep(1000);
    _thread = nil;
    ds_unload(_emu);
}

- (void)loop {
    uint64_t deadline = [self nanosNow];

    while (_running) {
        // Entrada: estado compartido, leído una vez por fotograma.
        ds_set_keys(_emu, _keys.load());
        uint32_t touch = _touch.load();
        ds_set_touch(_emu, (touch >> 8) & 0xFF, touch & 0xFF, (touch & 0x80000000u) != 0);

        uint64_t start = [self nanosNow];
        ds_run_frame(_emu);
        uint64_t spent = [self nanosNow] - start;

        _frames.fetch_add(1);
        _coreNanos.fetch_add(spent);

        // Al hilo principal sólo si ya pintó el anterior: pintar nunca frena
        // al core, y lo que se mide es el core.
        //
        // El @autoreleasepool es obligatorio: este hilo no tiene bucle de
        // eventos que vacíe la reserva por su cuenta, y sin él cada copia del
        // fotograma (384 KB) se quedaba viva para siempre. Eran ~22 MB por
        // segundo y iOS cerraba la app a los ~5 GB, a los cuatro minutos.
        if (!_framePending.exchange(true)) {
            @autoreleasepool {
                NSMutableData* pixels = [NSMutableData dataWithLength:DS_FRAME_PIXELS * 4];
                ds_copy_frame_rgba(_emu, static_cast<uint8_t*>(pixels.mutableBytes), pixels.length);
                void (^onFrame)(NSData*) = self.onFrame;
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (onFrame) onFrame(pixels);
                    self->_framePending = false;
                });
            }
        }

        if (self.unlimited) {
            deadline = [self nanosNow];
        } else {
            // Reloj absoluto: el error no se acumula de fotograma a fotograma.
            deadline += kFrameNanos;
            uint64_t now = [self nanosNow];
            if (deadline > now) {
                uint64_t wait = deadline - now;
                timespec ts{static_cast<time_t>(wait / 1000000000ull), static_cast<long>(wait % 1000000000ull)};
                nanosleep(&ts, nullptr);
            } else if (now - deadline > kFrameNanos * 4) {
                deadline = now;  // Muy atrás: no intentar recuperar a ráfagas.
            }
        }
    }
}

- (void)setButton:(int)bit pressed:(BOOL)pressed {
    if (pressed) {
        _keys.fetch_or(1u << bit);
    } else {
        _keys.fetch_and(~(1u << bit));
    }
}

- (void)touchAtX:(int)x y:(int)y down:(BOOL)down {
    x = MAX(0, MIN(255, x));
    y = MAX(0, MIN(191, y));
    _touch = down ? (0x80000000u | (uint32_t(x) << 8) | uint32_t(y)) : 0u;
}

- (void)takeStats:(double*)fps msPerFrame:(double*)ms {
    uint64_t frames = _frames.exchange(0);
    uint64_t nanos = _coreNanos.exchange(0);
    *fps = frames;
    *ms = frames ? (nanos / 1e6) / frames : 0;
}

@end

// ---------------------------------------------------------------------------
// La pantalla
// ---------------------------------------------------------------------------

// Mismo orden de bits que DsButton.kt.
enum { kA = 0, kB = 1, kSelect = 2, kStart = 3, kRight = 4, kLeft = 5, kUp = 6, kDown = 7, kR = 8, kL = 9, kX = 10, kY = 11 };

@interface SpikeViewController : UIViewController <UIDocumentPickerDelegate>
@end

@implementation SpikeViewController {
    DsCore* _core;
    UIImageView* _screen;
    UILabel* _stats;
    NSTimer* _timer;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.blackColor;
    _core = [DsCore new];

    _stats = [UILabel new];
    _stats.textColor = UIColor.whiteColor;
    _stats.numberOfLines = 0;
    _stats.font = [UIFont monospacedDigitSystemFontOfSize:15 weight:UIFontWeightRegular];
    _stats.text = @"Elige una ROM para empezar.";

    UIButton* pick = [UIButton buttonWithType:UIButtonTypeSystem];
    [pick setTitle:@"Elegir ROM" forState:UIControlStateNormal];
    [pick addTarget:self action:@selector(pickROM) forControlEvents:UIControlEventTouchUpInside];

    UISegmentedControl* speed = [[UISegmentedControl alloc] initWithItems:@[ @"60 Hz", @"Sin límite" ]];
    speed.selectedSegmentIndex = 1;
    _core.unlimited = YES;
    speed.selectedSegmentTintColor = UIColor.systemBlueColor;
    [speed addTarget:self action:@selector(speedChanged:) forControlEvents:UIControlEventValueChanged];

    UIStackView* top = [[UIStackView alloc] initWithArrangedSubviews:@[ pick, speed ]];
    top.spacing = 16;

    _screen = [UIImageView new];
    _screen.contentMode = UIViewContentModeScaleAspectFit;
    _screen.layer.magnificationFilter = kCAFilterNearest;
    _screen.backgroundColor = [UIColor colorWithWhite:0.1 alpha:1];
    _screen.userInteractionEnabled = YES;

    UIStackView* pad = [UIStackView new];
    pad.distribution = UIStackViewDistributionFillEqually;
    pad.spacing = 6;
    NSArray* buttons = @[
        @[ @"←", @(kLeft) ], @[ @"↑", @(kUp) ], @[ @"↓", @(kDown) ], @[ @"→", @(kRight) ],
        @[ @"L", @(kL) ], @[ @"R", @(kR) ], @[ @"Y", @(kY) ], @[ @"X", @(kX) ],
        @[ @"B", @(kB) ], @[ @"A", @(kA) ], @[ @"Sel", @(kSelect) ], @[ @"Start", @(kStart) ],
    ];
    for (NSArray* b in buttons) {
        UIButton* button = [UIButton buttonWithType:UIButtonTypeSystem];
        [button setTitle:b[0] forState:UIControlStateNormal];
        button.tag = [b[1] intValue];
        button.backgroundColor = [UIColor colorWithWhite:0.2 alpha:1];
        button.layer.cornerRadius = 8;
        [button addTarget:self action:@selector(buttonDown:) forControlEvents:UIControlEventTouchDown];
        [button addTarget:self action:@selector(buttonUp:)
                 forControlEvents:UIControlEventTouchUpInside | UIControlEventTouchUpOutside | UIControlEventTouchCancel];
        [pad addArrangedSubview:button];
    }

    UIStackView* column = [[UIStackView alloc] initWithArrangedSubviews:@[ top, _stats, _screen, pad ]];
    column.axis = UILayoutConstraintAxisVertical;
    column.spacing = 12;
    column.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:column];
    UILayoutGuide* safe = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [column.leadingAnchor constraintEqualToAnchor:safe.leadingAnchor constant:16],
        [column.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-16],
        [column.topAnchor constraintEqualToAnchor:safe.topAnchor constant:12],
        [column.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-12],
        [pad.heightAnchor constraintEqualToConstant:52],
    ]];

    __weak UIImageView* screen = _screen;
    _core.onFrame = ^(NSData* pixels) {
        // ds_copy_frame_rgba: cuatro bytes R, G, B, A por píxel.
        CGDataProviderRef provider = CGDataProviderCreateWithCFData((__bridge CFDataRef)pixels);
        CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
        CGImageRef image = CGImageCreate(kWidth, kHeight, 8, 32, kWidth * 4, space,
                                         kCGImageAlphaNoneSkipLast | kCGBitmapByteOrderDefault,
                                         provider, nullptr, false, kCGRenderingIntentDefault);
        screen.image = [UIImage imageWithCGImage:image];
        CGImageRelease(image);
        CGColorSpaceRelease(space);
        CGDataProviderRelease(provider);
    };

    _timer = [NSTimer scheduledTimerWithTimeInterval:1.0 target:self selector:@selector(tick) userInfo:nil repeats:YES];
}

// Memoria de la app tal como la cuenta iOS para decidir si la cierra
// (phys_footprint), en MB.
static double FootprintMB() {
    task_vm_info_data_t info{};
    mach_msg_type_number_t count = TASK_VM_INFO_COUNT;
    if (task_info(mach_task_self(), TASK_VM_INFO, reinterpret_cast<task_info_t>(&info), &count) != KERN_SUCCESS) return 0;
    return info.phys_footprint / (1024.0 * 1024.0);
}

- (void)tick {
    double fps = 0, ms = 0;
    [_core takeStats:&fps msPerFrame:&ms];
    if (fps == 0) return;
    double memory = FootprintMB();
    // 59,8261 fotogramas por segundo es una DS real.
    _stats.text = [NSString stringWithFormat:@"%.0f fps · %.2f ms por fotograma · %.0f %% de una DS · %.0f MB",
                   fps, ms, ms > 0 ? (16.715113 / ms) * 100.0 : 0, memory];
    NSLog(@"[spike] %.0f fps, %.2f ms/fotograma, %.0f%% de una DS, %.0f MB", fps, ms, (16.715113 / ms) * 100.0, memory);
}

- (void)speedChanged:(UISegmentedControl*)control {
    _core.unlimited = control.selectedSegmentIndex == 1;
}

- (void)buttonDown:(UIButton*)button { [_core setButton:(int)button.tag pressed:YES]; }
- (void)buttonUp:(UIButton*)button { [_core setButton:(int)button.tag pressed:NO]; }

- (void)pickROM {
    NSArray* types = @[ UTTypeData ];
    UIDocumentPickerViewController* picker =
        [[UIDocumentPickerViewController alloc] initForOpeningContentTypes:types asCopy:YES];
    picker.delegate = self;
    [self presentViewController:picker animated:YES completion:nil];
}

- (void)documentPicker:(UIDocumentPickerViewController*)controller didPickDocumentsAtURLs:(NSArray<NSURL*>*)urls {
    NSData* rom = [NSData dataWithContentsOfURL:urls.firstObject];
    if (!rom) {
        _stats.text = @"No se pudo leer el archivo.";
        return;
    }
    NSString* error = [_core loadROM:rom];
    _stats.text = error ?: [NSString stringWithFormat:@"%@ cargada. Midiendo…", urls.firstObject.lastPathComponent];
}

// El lápiz: sólo la mitad de abajo de la imagen, convertida a 256×192.
- (void)touchesBegan:(NSSet<UITouch*>*)touches withEvent:(UIEvent*)event { [self handleTouch:touches.anyObject down:YES]; }
- (void)touchesMoved:(NSSet<UITouch*>*)touches withEvent:(UIEvent*)event { [self handleTouch:touches.anyObject down:YES]; }
- (void)touchesEnded:(NSSet<UITouch*>*)touches withEvent:(UIEvent*)event { [_core touchAtX:0 y:0 down:NO]; }
- (void)touchesCancelled:(NSSet<UITouch*>*)touches withEvent:(UIEvent*)event { [_core touchAtX:0 y:0 down:NO]; }

- (void)handleTouch:(UITouch*)touch down:(BOOL)down {
    CGPoint p = [touch locationInView:_screen];
    CGSize box = _screen.bounds.size;
    CGFloat scale = MIN(box.width / kWidth, box.height / kHeight);
    CGFloat left = (box.width - kWidth * scale) / 2;
    CGFloat top = (box.height - kHeight * scale) / 2;
    CGFloat x = (p.x - left) / scale;
    CGFloat y = (p.y - top) / scale - 192;
    if (x < 0 || x >= kWidth || y < 0 || y >= 192) {
        [_core touchAtX:0 y:0 down:NO];
        return;
    }
    [_core touchAtX:(int)x y:(int)y down:down];
}

@end

// ---------------------------------------------------------------------------

@interface SpikeAppDelegate : UIResponder <UIApplicationDelegate>
@property(strong, nonatomic) UIWindow* window;
@end

@implementation SpikeAppDelegate
- (BOOL)application:(UIApplication*)application didFinishLaunchingWithOptions:(NSDictionary*)options {
    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    self.window.rootViewController = [SpikeViewController new];
    [self.window makeKeyAndVisible];
    // Que la pantalla no se apague a mitad de una medición.
    application.idleTimerDisabled = YES;
    return YES;
}
@end

int main(int argc, char* argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([SpikeAppDelegate class]));
    }
}
