// Vibe Windows render-loopback bridge. Uses the public WASAPI COM ABI; no microphone input.
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;

namespace VibeAudio {
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class DeviceEnumerator { }
    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator {
        [PreserveSig] int EnumAudioEndpoints(int flow, uint mask, out IntPtr devices);
        [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IMMDevice device);
        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
        [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr callback);
        [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr callback);
    }
    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice {
        [PreserveSig] int Activate(ref Guid iid, uint context, IntPtr activation, [MarshalAs(UnmanagedType.IUnknown)] out object result);
        [PreserveSig] int OpenPropertyStore(uint access, out IntPtr properties);
        [PreserveSig] int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetState(out uint state);
    }
    [ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioClient {
        [PreserveSig] int Initialize(int shareMode, uint flags, long duration, long periodicity, IntPtr format, IntPtr session);
        [PreserveSig] int GetBufferSize(out uint frames);
        [PreserveSig] int GetStreamLatency(out long latency);
        [PreserveSig] int GetCurrentPadding(out uint frames);
        [PreserveSig] int IsFormatSupported(int mode, IntPtr format, out IntPtr closest);
        [PreserveSig] int GetMixFormat(out IntPtr format);
        [PreserveSig] int GetDevicePeriod(out long normal, out long minimum);
        [PreserveSig] int Start();
        [PreserveSig] int Stop();
        [PreserveSig] int Reset();
        [PreserveSig] int SetEventHandle(IntPtr handle);
        [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
    }
    [ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioCaptureClient {
        [PreserveSig] int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong device, out ulong time);
        [PreserveSig] int ReleaseBuffer(uint frames);
        [PreserveSig] int GetNextPacketSize(out uint frames);
    }
    public sealed class Loopback : IDisposable {
        IMMDeviceEnumerator enumerator;
        IMMDevice device;
        IAudioClient client;
        IAudioCaptureClient capture;
        int channels, bits, block;
        bool floating;
        string endpoint;
        public int SampleRate { get; private set; }
        static void Check(int result) { if (result < 0) Marshal.ThrowExceptionForHR(result); }
        public Loopback() {
            IntPtr format = IntPtr.Zero;
            try {
                enumerator = (IMMDeviceEnumerator)new DeviceEnumerator();
                Check(enumerator.GetDefaultAudioEndpoint(0, 0, out device));
                Check(device.GetId(out endpoint));
                Guid iid = typeof(IAudioClient).GUID; object value;
                Check(device.Activate(ref iid, 23, IntPtr.Zero, out value)); client = (IAudioClient)value;
                Check(client.GetMixFormat(out format));
                int tag = (ushort)Marshal.ReadInt16(format, 0);
                channels = (ushort)Marshal.ReadInt16(format, 2); SampleRate = Marshal.ReadInt32(format, 4);
                block = (ushort)Marshal.ReadInt16(format, 12); bits = (ushort)Marshal.ReadInt16(format, 14);
                if (tag == 65534) tag = Marshal.ReadInt32(format, 24);
                floating = tag == 3;
                if (channels < 1 || channels > 32 || block < channels || (tag != 1 && tag != 3) ||
                    (floating ? bits != 32 : bits != 16 && bits != 24 && bits != 32)) throw new NotSupportedException("Unsupported output format");
                Check(client.Initialize(0, 0x00020000, 1000000, 0, format, IntPtr.Zero));
                iid = typeof(IAudioCaptureClient).GUID;
                Check(client.GetService(ref iid, out value)); capture = (IAudioCaptureClient)value;
                Check(client.Start());
            } catch { Dispose(); throw; }
            finally { if (format != IntPtr.Zero) Marshal.FreeCoTaskMem(format); }
        }
        public bool IsDefaultDevice() {
            IMMDevice current = null;
            try { Check(enumerator.GetDefaultAudioEndpoint(0, 0, out current)); string id; Check(current.GetId(out id)); return id == endpoint; }
            finally { if (current != null) Marshal.ReleaseComObject(current); }
        }
        public byte[] Read() {
            var samples = new List<float>(); uint pending;
            Check(capture.GetNextPacketSize(out pending));
            while (pending > 0) {
                IntPtr data; uint frames, flags; ulong position, time;
                Check(capture.GetBuffer(out data, out frames, out flags, out position, out time));
                try {
                    if (frames > 192000) throw new InvalidOperationException("Invalid audio packet");
                    bool silent = (flags & 2) != 0;
                    byte[] bytes = new byte[checked((int)frames * block)];
                    if (!silent) Marshal.Copy(data, bytes, 0, bytes.Length);
                    int bytesPerSample = bits / 8;
                    for (int i = 0; i < frames; i++) {
                        float sum = 0;
                        if (!silent) for (int c = 0; c < channels; c++) {
                            int offset = i * block + c * bytesPerSample;
                            if (floating) sum += BitConverter.ToSingle(bytes, offset);
                            else if (bits == 16) sum += BitConverter.ToInt16(bytes, offset) / 32768f;
                            else if (bits == 32) sum += BitConverter.ToInt32(bytes, offset) / 2147483648f;
                            else { int n = bytes[offset] | bytes[offset+1] << 8 | bytes[offset+2] << 16; n = (n << 8) >> 8; sum += n / 8388608f; }
                        }
                        samples.Add(sum / channels);
                    }
                } finally { Check(capture.ReleaseBuffer(frames)); }
                Check(capture.GetNextPacketSize(out pending));
                if (samples.Count >= 8192) break;
            }
            float[] mono = samples.ToArray(); byte[] output = new byte[mono.Length * 4];
            Buffer.BlockCopy(mono, 0, output, 0, output.Length); return output;
        }
        public void Dispose() {
            if (client != null) client.Stop();
            if (capture != null) { Marshal.ReleaseComObject(capture); capture = null; }
            if (client != null) { Marshal.ReleaseComObject(client); client = null; }
            if (device != null) { Marshal.ReleaseComObject(device); device = null; }
            if (enumerator != null) { Marshal.ReleaseComObject(enumerator); enumerator = null; }
        }
    }
}
