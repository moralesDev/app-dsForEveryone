// Genera los iconos de la app (Android e iOS) y el logo que se ve dentro de
// ella (composeResources) a partir de art/logo.png.
//
//   swift tools/make-icons.swift
//
// Sin dependencias: CoreGraphics e ImageIO, que trae macOS. Si cambia el
// logo, se cambia art/logo.png y se vuelve a ejecutar.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let logoURL = root.appendingPathComponent("art/logo.png")

// El fondo oscuro de la app (ic_launcher_background, ui/theme Background).
let background = CGColor(red: 0x16 / 255.0, green: 0x18 / 255.0, blue: 0x26 / 255.0, alpha: 1)

guard let source = CGImageSourceCreateWithURL(logoURL as CFURL, nil),
      let logo = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("No se pudo leer \(logoURL.path)")
}

/// Un PNG cuadrado de [size] px con el logo centrado ocupando [fraction] del lado.
func render(size: Int, fraction: Double, fill: CGColor?, to path: String) {
    let space = CGColorSpace(name: CGColorSpace.sRGB)!
    guard let context = CGContext(
        data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
        space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else { fatalError("Sin contexto") }
    context.interpolationQuality = .high
    if let fill {
        context.setFillColor(fill)
        context.fill(CGRect(x: 0, y: 0, width: size, height: size))
    }
    let side = Double(size) * fraction
    let origin = (Double(size) - side) / 2
    context.draw(logo, in: CGRect(x: origin, y: origin, width: side, height: side))
    guard let image = context.makeImage() else { fatalError("Sin imagen") }

    let url = root.appendingPathComponent(path)
    try! FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("No se pudo escribir \(path)") }
    print("\(path) (\(size)×\(size))")
}

// Android: capa frontal del icono adaptable. Mide 108 dp; el sistema enseña
// sólo el círculo/forma central de 72 dp y garantiza intacto el de 66 dp, así
// que el logo va en 66/108 del lado. Transparente: el fondo es otra capa.
let androidDensities = ["mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432]
for (density, size) in androidDensities.sorted(by: { $0.value < $1.value }) {
    render(size: size, fraction: 66.0 / 108.0, fill: nil,
           to: "app/src/main/res/drawable-\(density)/ic_launcher_foreground.png")
}

// iOS: un solo icono de 1024, opaco (Apple no admite transparencia). El
// sistema redondea las esquinas; el logo deja margen para que no las toque.
render(size: 1024, fraction: 0.86, fill: background,
       to: "ios/app/Assets.xcassets/AppIcon.appiconset/AppIcon.png")

// Dentro de la app (AppLogo: arranque, bienvenida, carga, biblioteca), común a
// las dos plataformas. Como mucho se pinta a 112 dp, unos 450 px en las
// pantallas más densas: 512 px sobran y pesan mucho menos que el original.
render(size: 512, fraction: 1.0, fill: nil,
       to: "shared/src/commonMain/composeResources/drawable/app_logo.png")
