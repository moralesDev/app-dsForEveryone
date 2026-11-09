/*
 * DsForEveryone para iOS y iPadOS: el arranque.
 *
 * Toda la app vive en el framework `Shared` (Kotlin Multiplatform, ver
 * shared/ y docs/ios.md). Aquí sólo se crea la ventana y se le pone como
 * raíz el UIViewController de Compose que devuelve MainViewController().
 */
#import <UIKit/UIKit.h>
#import <Shared/Shared.h>

@interface AppDelegate : UIResponder <UIApplicationDelegate>
@property(strong, nonatomic) UIWindow* window;
@end

@implementation AppDelegate
- (BOOL)application:(UIApplication*)application didFinishLaunchingWithOptions:(NSDictionary*)options {
    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    self.window.rootViewController = [SharedMainViewControllerKt MainViewController];
    [self.window makeKeyAndVisible];
    return YES;
}
@end

int main(int argc, char* argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([AppDelegate class]));
    }
}
