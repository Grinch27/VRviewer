#extension GL_OES_EGL_image_external : require
// Open decisions: calibrated lens distortion and HDR tone mapping are not implemented.
// Verification: logical top-left eye crop MUST precede the SurfaceTexture transform.
precision highp float;
uniform samplerExternalOES uTexture;
uniform mat4 uTextureTransform;
uniform mat4 uCameraRotation;
uniform vec4 uEyeRect;
uniform vec2 uTexel;
uniform float uViewportAspect;
uniform float uVideoAspect;
uniform float uTanHalfFov;
uniform int uProjection;
varying vec2 vPosition;
const float PI = 3.141592653589793;
void main() {
    vec2 uv;
    if (uProjection == 0) {
        vec2 fit = vec2(min(1.0, uVideoAspect / uViewportAspect), min(1.0, uViewportAspect / uVideoAspect));
        vec2 p = vPosition / fit;
        if (abs(p.x) > 1.0 || abs(p.y) > 1.0) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        uv = vec2(p.x * 0.5 + 0.5, 0.5 - p.y * 0.5);
    } else {
        vec3 ray = normalize((uCameraRotation * vec4(vPosition.x * uViewportAspect * uTanHalfFov, vPosition.y * uTanHalfFov, -1.0, 0.0)).xyz);
        float longitude = atan(ray.x, -ray.z);
        float latitude = asin(clamp(ray.y, -1.0, 1.0));
        if (uProjection == 1 && abs(longitude) > PI * 0.5) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        float span = uProjection == 1 ? PI : 2.0 * PI;
        uv = vec2(longitude / span + 0.5, 0.5 - latitude / PI);
    }
    vec2 cropped = clamp(uEyeRect.xy + uv * uEyeRect.zw, uEyeRect.xy + uTexel * 0.5, uEyeRect.xy + uEyeRect.zw - uTexel * 0.5);
    vec2 textureUv = (uTextureTransform * vec4(cropped.x, 1.0 - cropped.y, 0.0, 1.0)).xy;
    gl_FragColor = texture2D(uTexture, textureUv);
}
