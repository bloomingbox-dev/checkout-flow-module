Pod::Spec.new do |s|
  s.name           = 'CheckoutModule'
  s.version        = '1.0.0'
  s.summary        = 'A sample project summary'
  s.description    = 'A sample project description'
  s.author         = ''
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '15.1',
    :tvos => '15.1'
  }
  s.source         = { git: '' }
  s.static_framework = true
  checkout_components_version = '1.6.0'

  s.dependency 'ExpoModulesCore'
  s.prepare_command = <<-CMD
    set -e
    rm -rf vendor
    mkdir -p vendor

    curl -L --fail --retry 3 -o /tmp/CheckoutComponentsSDK.xcframework.zip "https://github.com/checkout/checkout-ios-components/releases/download/#{checkout_components_version}/CheckoutComponentsSDK.xcframework.zip"
    unzip -o -q /tmp/CheckoutComponentsSDK.xcframework.zip -d vendor
    rm -rf vendor/__MACOSX
  CMD
  s.preserve_paths = 'vendor/**/*'
  s.vendored_frameworks = 'vendor/CheckoutComponentsSDK.xcframework'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "CheckoutModule.swift"
end
